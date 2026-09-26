package com.tabletap.domain;

import java.util.Map;
import java.util.Set;

public enum OrderStatus {
    SENT, IN_PROGRESS, READY, SERVED, CANCELLED;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = Map.of(
        SENT, Set.of(IN_PROGRESS, CANCELLED),
        IN_PROGRESS, Set.of(READY, CANCELLED),
        READY, Set.of(SERVED, CANCELLED),
        SERVED, Set.of(),
        CANCELLED, Set.of());

    public boolean isOpen() {
        return this != SERVED && this != CANCELLED;
    }

    public boolean canMoveTo(OrderStatus next) {
        return ALLOWED.get(this).contains(next);
    }
}
