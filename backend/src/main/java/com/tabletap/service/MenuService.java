package com.tabletap.service;

import com.tabletap.domain.AppUser;
import com.tabletap.domain.MenuCategory;
import com.tabletap.domain.MenuItem;
import com.tabletap.domain.Restaurant;
import com.tabletap.dto.MenuDtos.*;
import com.tabletap.live.LiveEvent;
import com.tabletap.repository.MenuCategoryRepository;
import org.springframework.context.ApplicationEventPublisher;
import com.tabletap.repository.MenuItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/** The per-restaurant menu template waiters build orders from. */
@Service
@RequiredArgsConstructor
@Transactional
public class MenuService {
    private final MenuCategoryRepository categories;
    private final MenuItemRepository items;
    private final RestaurantService restaurants;
    private final AccessService access;
    private final ApplicationEventPublisher events;

    private void changed(AppUser u, Restaurant r) {
        events.publishEvent(LiveEvent.of(LiveEvent.MENU_CHANGED, r, u.getId()));
    }

    @Transactional(readOnly = true)
    public List<CategoryView> menu(AppUser u, Long restaurantId) {
        Restaurant r = restaurants.load(restaurantId);
        access.requireWork(u, r);
        return categories.findByRestaurantIdOrderBySortOrderAscNameAsc(restaurantId).stream().map(CategoryView::of).toList();
    }

    public CategoryView createCategory(AppUser u, Long restaurantId, CategoryRequest req) {
        Restaurant r = restaurants.load(restaurantId);
        access.requireManage(u, r);
        MenuCategory c = new MenuCategory();
        c.setRestaurant(r);
        c.setName(req.name().trim());
        c.setSortOrder(req.sortOrder() == null ? 0 : req.sortOrder());
        categories.save(c);
        changed(u, r);
        return CategoryView.of(c);
    }

    public CategoryView updateCategory(AppUser u, Long id, CategoryRequest req) {
        MenuCategory c = loadCategory(u, id);
        c.setName(req.name().trim());
        if (req.sortOrder() != null) c.setSortOrder(req.sortOrder());
        changed(u, c.getRestaurant());
        return CategoryView.of(c);
    }

    public void deleteCategory(AppUser u, Long id) {
        MenuCategory c = loadCategory(u, id);
        categories.delete(c);
        changed(u, c.getRestaurant());
    }

    public ItemView createItem(AppUser u, ItemRequest req) {
        MenuCategory c = loadCategory(u, req.categoryId());
        MenuItem i = new MenuItem();
        i.setCategory(c);
        apply(i, req);
        c.getItems().add(i);
        items.save(i);
        changed(u, c.getRestaurant());
        return ItemView.of(i);
    }

    public ItemView updateItem(AppUser u, Long id, ItemRequest req) {
        MenuItem i = loadItem(u, id);
        if (!i.getCategory().getId().equals(req.categoryId())) {
            MenuCategory target = loadCategory(u, req.categoryId());
            if (!target.getRestaurant().getId().equals(i.getCategory().getRestaurant().getId()))
                throw ApiException.badRequest("Category belongs to another restaurant");
            i.setCategory(target);
        }
        apply(i, req);
        changed(u, i.getCategory().getRestaurant());
        return ItemView.of(i);
    }

    /** Quick "86" toggle — chefs can do this, not just owners. */
    public ItemView setAvailable(AppUser u, Long id, boolean available) {
        MenuItem i = items.findById(id).orElseThrow(() -> ApiException.notFound("Menu item"));
        Restaurant r = i.getCategory().getRestaurant();
        access.requireKitchen(u, r);
        i.setAvailable(available);
        changed(u, r);
        return ItemView.of(i);
    }

    public void deleteItem(AppUser u, Long id) {
        MenuItem i = loadItem(u, id);
        i.getCategory().getItems().remove(i);
        changed(u, i.getCategory().getRestaurant());
    }

    private void apply(MenuItem i, ItemRequest req) {
        i.setName(req.name().trim());
        i.setDescription(req.description());
        i.setPrice(req.price());
        if (req.available() != null) i.setAvailable(req.available());
        i.getOptions().clear();
        if (req.options() != null) {
            req.options().stream().filter(Objects::nonNull).map(String::trim)
                .filter(s -> !s.isEmpty()).distinct().forEach(i.getOptions()::add);
        }
    }

    private MenuCategory loadCategory(AppUser u, Long id) {
        MenuCategory c = categories.findById(id).orElseThrow(() -> ApiException.notFound("Category"));
        access.requireManage(u, c.getRestaurant());
        return c;
    }

    private MenuItem loadItem(AppUser u, Long id) {
        MenuItem i = items.findById(id).orElseThrow(() -> ApiException.notFound("Menu item"));
        access.requireManage(u, i.getCategory().getRestaurant());
        return i;
    }
}
