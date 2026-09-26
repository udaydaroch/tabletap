package com.tabletap.dto;

import com.tabletap.dto.OrderDtos.OrderView;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Every order for one day (all statuses — nothing is ever deleted), plus a summary.
 * summary is null for staff without owner-level access, so waiters don't see revenue.
 */
public record DayReport(Long restaurantId, String restaurantName, LocalDate date, String timeZone,
                        List<OrderView> orders, Summary summary) {

    public record Summary(long totalOrders, long served, long open, long cancelled, BigDecimal revenue,
                          BigDecimal averageOrder, List<ItemTotal> items, List<WaiterTotal> waiters) {}

    public record ItemTotal(String itemName, long quantity, BigDecimal revenue) {}

    public record WaiterTotal(String waiterName, long orders, BigDecimal revenue) {}
}
