package com.tabletap.live;

import com.tabletap.domain.Restaurant;

/**
 * A "something changed" notification pushed to connected browsers. Carries ids only, never data:
 * clients re-fetch through the normal (access-checked) API.
 */
public record LiveEvent(String type, Long restaurantId, Long ownerId, Long userId) {
    public static final String SHIFT_CHANGED = "SHIFT_CHANGED";
    public static final String ORDER_CREATED = "ORDER_CREATED";
    public static final String ORDER_UPDATED = "ORDER_UPDATED";
    public static final String RESTAURANT_CHANGED = "RESTAURANT_CHANGED";
    public static final String STAFF_CHANGED = "STAFF_CHANGED";
    public static final String MENU_CHANGED = "MENU_CHANGED";
    public static final String OWNER_CHANGED = "OWNER_CHANGED";
    public static final String FLOOR_CHANGED = "FLOOR_CHANGED";

    public static LiveEvent of(String type, Restaurant r, Long userId) {
        return new LiveEvent(type, r.getId(), r.getOwner().getId(), userId);
    }

    public static LiveEvent owner(Long ownerId) {
        return new LiveEvent(OWNER_CHANGED, null, ownerId, ownerId);
    }
}
