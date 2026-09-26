package com.tabletap.stock;

import com.tabletap.domain.*;
import com.tabletap.dto.StockDtos.*;
import com.tabletap.live.LiveEvent;
import com.tabletap.repository.*;
import com.tabletap.service.AccessService;
import com.tabletap.service.ApiException;
import com.tabletap.service.RestaurantService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * Stock tracking. Observer: listens for OrderPlaced *inside* the order's transaction, so stock and the
 * order are saved (or rolled back) together. Specifications (StockRules) decide when to alert the owner
 * and when a dish can't be made any more and should switch itself off.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class StockService {
    private final IngredientRepository ingredients;
    private final RecipeLineRepository recipes;
    private final StockAlertRepository alerts;
    private final MenuItemRepository items;
    private final OrderRepository orders;
    private final RestaurantService restaurants;
    private final AccessService access;
    private final ApplicationEventPublisher events;

    // ---------------------------------------------------------------- the observer

    @EventListener
    public void onOrderPlaced(OrderPlaced event) {
        CustomerOrder order = orders.findById(event.orderId()).orElseThrow();
        Map<Long, Integer> portions = new HashMap<>();
        order.getLines().forEach(l -> portions.merge(l.getMenuItemId(), l.getQuantity(), Integer::sum));
        List<RecipeLine> used = recipes.findByMenuItemIdIn(portions.keySet());
        if (used.isEmpty()) return; // this restaurant doesn't track stock for these dishes

        Set<Ingredient> touched = new HashSet<>();
        for (RecipeLine line : used) {
            Ingredient ing = line.getIngredient();
            boolean wasLow = StockRules.LOW.isSatisfiedBy(ing);
            ing.setStock(ing.getStock().subtract(line.getQuantity().multiply(BigDecimal.valueOf(portions.get(line.getMenuItem().getId())))));
            ing.setUpdatedAt(Instant.now());
            touched.add(ing);
            if (!wasLow && StockRules.LOW.isSatisfiedBy(ing)) alert(ing, StockRules.EMPTY.isSatisfiedBy(ing)
                ? ing.getName() + " has run out"
                : ing.getName() + " is running low (" + ing.getStock().stripTrailingZeros().toPlainString() + " " + unit(ing) + " left)");
        }
        reevaluateDishes(order.getRestaurant(), touched, null);
    }

    // ---------------------------------------------------------------- ingredients

    @Transactional(readOnly = true)
    public List<IngredientView> list(AppUser u, Long restaurantId) {
        access.requireKitchen(u, restaurants.load(restaurantId));
        return ingredients.findByRestaurantIdOrderByName(restaurantId).stream().map(IngredientView::of).toList();
    }

    public IngredientView create(AppUser u, Long restaurantId, IngredientRequest req) {
        Restaurant r = restaurants.load(restaurantId);
        access.requireManage(u, r);
        Ingredient i = new Ingredient();
        i.setRestaurant(r);
        apply(i, req);
        ingredients.save(i);
        changed(u, r);
        return IngredientView.of(i);
    }

    public IngredientView update(AppUser u, Long id, IngredientRequest req) {
        Ingredient i = load(id);
        access.requireManage(u, i.getRestaurant());
        apply(i, req);
        reevaluateDishes(i.getRestaurant(), Set.of(i), u);
        changed(u, i.getRestaurant());
        return IngredientView.of(i);
    }

    public void delete(AppUser u, Long id) {
        Ingredient i = load(id);
        access.requireManage(u, i.getRestaurant());
        alerts.deleteByIngredientId(id);
        recipes.deleteByIngredientId(id);
        ingredients.delete(i);
        changed(u, i.getRestaurant());
    }

    /** Chefs and owners add delivered stock (or correct a count with a negative amount). */
    public IngredientView restock(AppUser u, Long id, BigDecimal amount) {
        Ingredient i = load(id);
        access.requireKitchen(u, i.getRestaurant());
        i.setStock(i.getStock().add(amount));
        i.setUpdatedAt(Instant.now());
        reevaluateDishes(i.getRestaurant(), Set.of(i), u);
        changed(u, i.getRestaurant());
        return IngredientView.of(i);
    }

    // ---------------------------------------------------------------- recipes

    @Transactional(readOnly = true)
    public List<RecipeLineView> recipe(AppUser u, Long itemId) {
        MenuItem item = items.findById(itemId).orElseThrow(() -> ApiException.notFound("Menu item"));
        access.requireKitchen(u, item.getCategory().getRestaurant());
        return item.getRecipe().stream().map(RecipeLineView::of).toList();
    }

    public List<RecipeLineView> setRecipe(AppUser u, Long itemId, RecipeRequest req) {
        MenuItem item = items.findById(itemId).orElseThrow(() -> ApiException.notFound("Menu item"));
        Restaurant r = item.getCategory().getRestaurant();
        access.requireManage(u, r);
        item.getRecipe().clear();
        items.flush();
        Set<Long> seen = new HashSet<>();
        for (RecipeLineRequest l : req.lines()) {
            if (!seen.add(l.ingredientId())) throw ApiException.badRequest("An ingredient is listed twice");
            Ingredient ing = load(l.ingredientId());
            if (!ing.getRestaurant().getId().equals(r.getId())) throw ApiException.badRequest("Ingredient belongs to another restaurant");
            RecipeLine line = new RecipeLine();
            line.setMenuItem(item);
            line.setIngredient(ing);
            line.setQuantity(l.quantity());
            item.getRecipe().add(line);
        }
        applyRule(item, u);
        events.publishEvent(LiveEvent.of(LiveEvent.MENU_CHANGED, r, u.getId()));
        return item.getRecipe().stream().map(RecipeLineView::of).toList();
    }

    // ---------------------------------------------------------------- alerts

    @Transactional(readOnly = true)
    public List<AlertView> alerts(AppUser u, Long restaurantId) {
        access.requireKitchen(u, restaurants.load(restaurantId));
        return alerts.findByRestaurantIdAndAcknowledgedFalseOrderByCreatedAtDesc(restaurantId).stream().map(AlertView::of).toList();
    }

    public void acknowledge(AppUser u, Long alertId) {
        StockAlert a = alerts.findById(alertId).orElseThrow(() -> ApiException.notFound("Alert"));
        access.requireKitchen(u, a.getRestaurant());
        a.setAcknowledged(true);
        changed(u, a.getRestaurant());
    }

    // ---------------------------------------------------------------- internals

    /** Switch dishes off when they can't be made, and back on when stock returns (only if we switched them off). */
    private void reevaluateDishes(Restaurant r, Set<Ingredient> touched, AppUser u) {
        List<Long> ids = touched.stream().map(Ingredient::getId).toList();
        Set<MenuItem> affected = new HashSet<>();
        recipes.findByIngredientIdIn(ids).forEach(l -> affected.add(l.getMenuItem()));
        boolean menuChanged = false;
        for (MenuItem item : affected) menuChanged |= applyRule(item, u);
        if (menuChanged) events.publishEvent(LiveEvent.of(LiveEvent.MENU_CHANGED, r, u == null ? null : u.getId()));
        events.publishEvent(LiveEvent.of(LiveEvent.STOCK_CHANGED, r, u == null ? null : u.getId()));
    }

    private boolean applyRule(MenuItem item, AppUser u) {
        boolean canMake = StockRules.CAN_MAKE_ONE.isSatisfiedBy(item);
        if (!canMake && item.isAvailable()) {
            item.setAvailable(false);
            item.setAutoUnavailable(true);
            alert(item.getRecipe().get(0).getIngredient(), item.getName() + " switched off automatically (not enough stock)");
            return true;
        }
        if (canMake && !item.isAvailable() && item.isAutoUnavailable()) {
            item.setAvailable(true);
            item.setAutoUnavailable(false);
            return true;
        }
        return false;
    }

    private void alert(Ingredient ing, String message) {
        StockAlert a = new StockAlert();
        a.setRestaurant(ing.getRestaurant());
        a.setIngredient(ing);
        a.setMessage(message);
        alerts.save(a);
    }

    private Ingredient load(Long id) {
        return ingredients.findById(id).orElseThrow(() -> ApiException.notFound("Ingredient"));
    }

    private void apply(Ingredient i, IngredientRequest req) {
        i.setName(req.name().trim());
        i.setUnit(req.unit() == null || req.unit().isBlank() ? "portions" : req.unit().trim());
        if (req.stock() != null) i.setStock(req.stock());
        i.setLowThreshold(req.lowThreshold());
        i.setUpdatedAt(Instant.now());
    }

    private static String unit(Ingredient i) {
        return i.getUnit() == null ? "" : i.getUnit();
    }

    private void changed(AppUser u, Restaurant r) {
        events.publishEvent(LiveEvent.of(LiveEvent.STOCK_CHANGED, r, u.getId()));
    }
}
