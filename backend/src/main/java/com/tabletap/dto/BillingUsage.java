package com.tabletap.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Current-month bill. Restaurant fees are prorated from the day a restaurant was added,
 * so creating a restaurant immediately adds its share of the monthly fee. The floor-plan add-on is
 * prorated the same way from the day the restaurant upgraded to advanced floor plans.
 */
public record BillingUsage(Long ownerId, LocalDate periodStart, LocalDate periodEnd, int daysInPeriod,
                           BigDecimal feePerRestaurant, BigDecimal feePerOrder, BigDecimal floorPlanFee, List<Line> lines,
                           long totalOrders, BigDecimal restaurantFees, BigDecimal orderFees, BigDecimal floorPlanFees,
                           BigDecimal estimatedTotal) {

    public record Line(Long restaurantId, String name, LocalDate billedFrom, int daysBilled,
                       BigDecimal restaurantFee, long orders, BigDecimal orderFees,
                       LocalDate floorPlanFrom, BigDecimal floorPlanFee, BigDecimal subtotal) {}
}
