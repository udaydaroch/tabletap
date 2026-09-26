package com.tabletap.domain;

/** Small domain events (Observer pattern) beyond OrderPlaced, used by cloud sync. */
public final class DomainEvents {
    private DomainEvents() {}

    public record OrderStatusChanged(Long orderId) {}

    public record BillPaid(Long billId) {}

    public record ShiftChanged(Long shiftId) {}
}
