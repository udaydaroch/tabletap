package com.tabletap.stock;

import com.tabletap.domain.Ingredient;
import com.tabletap.domain.MenuItem;

/** The stock rules, each one a Specification. */
public final class StockRules {
    private StockRules() {}

    /** Stock is at or below the owner's alert level. */
    public static final Specification<Ingredient> LOW = i -> i.getStock().compareTo(i.getLowThreshold()) <= 0;

    /** Nothing left at all. */
    public static final Specification<Ingredient> EMPTY = i -> i.getStock().signum() <= 0;

    /** There's enough of every ingredient to make one more portion of this dish. */
    public static final Specification<MenuItem> CAN_MAKE_ONE = item -> item.getRecipe().stream()
        .allMatch(line -> line.getIngredient().getStock().compareTo(line.getQuantity()) >= 0);
}
