package com.tabletap.domain;

/**
 * Account types. To add a new type (e.g. CHEF, HOST), add it here, then grant it
 * access in {@link com.tabletap.service.AccessService} and SecurityConfig.
 */
public enum Role {
    ADMIN,
    OWNER,
    WAITER,
    /** Kitchen staff: sees the kitchen queue, updates order status, marks dishes sold out. Can't take orders. */
    CHEF
}
