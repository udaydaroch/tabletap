package com.tabletap.config;

import com.tabletap.domain.*;
import com.tabletap.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.tabletap.service.FloorService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {
    private final AppProperties props;
    private final AppUserRepository users;
    private final RestaurantRepository restaurants;
    private final MenuCategoryRepository categories;
    private final FloorService floors;
    private final IngredientRepository ingredients;
    private final PasswordEncoder encoder;

    @Override
    @Transactional
    public void run(String... args) {
        if (!users.existsByEmailIgnoreCase(props.admin().email())) {
            user(props.admin().email(), props.admin().password(), "Platform Admin", Role.ADMIN, null, null);
            log.info("Created admin account {}", props.admin().email());
        }
        if (props.demo().enabled() && !users.existsByEmailIgnoreCase("owner@demo.test")) {
            seedDemo();
        }
    }

    private void seedDemo() {
        String pw = props.demo().password();
        AppUser owner = user("owner@demo.test", pw, "Olivia Owner", Role.OWNER, null, null);

        Restaurant bistro = new Restaurant();
        bistro.setName("Demo Bistro");
        bistro.setAddress("12 Queen St");
        bistro.setCuisine("Modern European");
        bistro.setOwner(owner);
        restaurants.save(bistro);

        Restaurant noodle = new Restaurant();
        noodle.setName("Noodle Bar");
        noodle.setCuisine("Asian");
        noodle.setOwner(owner);
        restaurants.save(noodle);

        user("manager@demo.test", pw, "Max Manager", Role.WAITER, "Manager", bistro);
        user("waiter@demo.test", pw, "Wendy Waiter", Role.WAITER, "Waiter", bistro);
        user("waiter2@demo.test", pw, "Will Waiter", Role.WAITER, "Waiter", noodle);
        user("chef@demo.test", pw, "Carlos Chef", Role.CHEF, "Head Chef", bistro);

        category(bistro, "Starters", 1,
            item("Garlic Bread", "9.50", List.of("Add cheese", "Gluten free")),
            item("Soup of the Day", "12.00", List.of()));
        MenuItem ribeye;
        category(bistro, "Mains", 2,
            ribeye = item("Ribeye Steak", "38.00", List.of("Rare", "Medium rare", "Medium", "Well done", "Pepper sauce", "Mushroom sauce")),
            item("Fish & Chips", "27.00", List.of("Extra lemon", "No tartare")),
            item("Mushroom Risotto", "26.00", List.of("Vegan", "Add chicken")));
        MenuCategory drinks = category(bistro, "Drinks", 3,
            item("Flat White", "5.00", List.of("Oat milk", "Extra shot", "Decaf")),
            item("House Red (glass)", "13.00", List.of()));
        drinks.setStation("BAR");                       // drinks go to the bar, not the kitchen
        ribeye.setStation("GRILL");                     // steaks go to the grill
        ribeye.setKitchenName("रिबआई स्टेक");            // shown on dockets for Hindi-reading chefs
        Ingredient steak = new Ingredient();            // stock tracking demo: 12 steaks, alert at 4
        steak.setRestaurant(bistro);
        steak.setName("Ribeye");
        steak.setUnit("portions");
        steak.setStock(new BigDecimal("12"));
        steak.setLowThreshold(new BigDecimal("4"));
        ingredients.save(steak);
        RecipeLine rl = new RecipeLine();
        rl.setMenuItem(ribeye);
        rl.setIngredient(steak);
        rl.setQuantity(BigDecimal.ONE);
        ribeye.getRecipe().add(rl);
        bistro.setTimeZone("Pacific/Auckland");
        category(noodle, "Noodles", 1,
            item("Pad Thai", "22.00", List.of("Mild", "Hot", "Extra hot", "No peanuts")),
            item("Ramen", "24.00", List.of("Extra egg", "Spicy")));

        bistro.setFloorPlanTier(Restaurant.FloorPlanTier.ADVANCED);
        bistro.setFloorPlanAdvancedSince(java.time.Instant.now());
        floors.applyTemplateInternal(bistro, "bistro");
        log.info("Seeded demo data (owner@demo.test / manager@demo.test / waiter@demo.test / chef@demo.test)");
    }

    private AppUser user(String email, String pw, String name, Role role, String title, Restaurant r) {
        AppUser u = new AppUser();
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(pw));
        u.setFullName(name);
        u.setRole(role);
        u.setTitle(title);
        u.setRestaurant(r);
        return users.save(u);
    }

    private MenuItem item(String name, String price, List<String> options) {
        MenuItem i = new MenuItem();
        i.setName(name);
        i.setPrice(new BigDecimal(price));
        i.getOptions().addAll(options);
        return i;
    }

    private MenuCategory category(Restaurant r, String name, int order, MenuItem... items) {
        MenuCategory c = new MenuCategory();
        c.setRestaurant(r);
        c.setName(name);
        c.setSortOrder(order);
        for (MenuItem i : items) {
            i.setCategory(c);
            c.getItems().add(i);
        }
        return categories.save(c);
    }
}
