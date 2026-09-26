package com.tabletap.service;

import com.tabletap.config.AppProperties;
import com.tabletap.domain.AppUser;
import com.tabletap.domain.Restaurant;
import com.tabletap.domain.Role;
import com.tabletap.dto.BillingUsage;
import com.tabletap.repository.OrderRepository;
import com.tabletap.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Usage-based pricing: monthly fee per restaurant (prorated by days active this month)
 * + fee per order. Hook a payment provider (e.g. Stripe metered billing) in here later.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BillingService {
    private final RestaurantRepository restaurants;
    private final OrderRepository orders;
    private final AppProperties props;

    public BillingUsage usage(AppUser u) {
        if (u.getRole() != Role.OWNER) throw ApiException.badRequest("Billing applies to owner accounts");
        return usageFor(u);
    }

    public BillingUsage usageFor(AppUser owner) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate start = today.withDayOfMonth(1);
        LocalDate end = today.withDayOfMonth(today.lengthOfMonth());
        int days = today.lengthOfMonth();
        var fees = props.billing();

        Map<Long, Long> orderCounts = new HashMap<>();
        for (Object[] row : orders.countPerRestaurantForOwnerSince(owner.getId(), start.atStartOfDay().toInstant(ZoneOffset.UTC)))
            orderCounts.put((Long) row[0], (Long) row[1]);

        List<BillingUsage.Line> lines = restaurants.findByOwnerIdOrderByName(owner.getId()).stream()
            .map(r -> line(r, start, days, orderCounts.getOrDefault(r.getId(), 0L), fees))
            .toList();

        BigDecimal restaurantFees = lines.stream().map(BillingUsage.Line::restaurantFee).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal orderFees = lines.stream().map(BillingUsage.Line::orderFees).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal floorFees = lines.stream().map(BillingUsage.Line::floorPlanFee).reduce(BigDecimal.ZERO, BigDecimal::add);
        long totalOrders = lines.stream().mapToLong(BillingUsage.Line::orders).sum();
        return new BillingUsage(owner.getId(), start, end, days, fees.monthlyFeePerRestaurant(), fees.feePerOrder(),
            fees.floorPlanFee(), lines, totalOrders, restaurantFees, orderFees, floorFees,
            restaurantFees.add(orderFees).add(floorFees));
    }

    private BillingUsage.Line line(Restaurant r, LocalDate periodStart, int days, long orderCount, AppProperties.Billing fees) {
        LocalDate from = later(r.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate(), periodStart);
        int daysBilled = days - from.getDayOfMonth() + 1;
        BigDecimal base = prorate(fees.monthlyFeePerRestaurant(), daysBilled, days);
        BigDecimal orderFees = fees.feePerOrder().multiply(BigDecimal.valueOf(orderCount)).setScale(2, RoundingMode.HALF_UP);

        LocalDate floorFrom = r.getFloorPlanTier() == Restaurant.FloorPlanTier.ADVANCED && r.getFloorPlanAdvancedSince() != null
            ? later(r.getFloorPlanAdvancedSince().atZone(ZoneOffset.UTC).toLocalDate(), periodStart) : null;
        BigDecimal floorFee = floorFrom == null ? BigDecimal.ZERO.setScale(2)
            : prorate(fees.floorPlanFee(), days - floorFrom.getDayOfMonth() + 1, days);

        return new BillingUsage.Line(r.getId(), r.getName(), from, daysBilled, base, orderCount, orderFees,
            floorFrom, floorFee, base.add(orderFees).add(floorFee));
    }

    private static LocalDate later(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private static BigDecimal prorate(BigDecimal monthly, int daysBilled, int days) {
        return monthly.multiply(BigDecimal.valueOf(daysBilled)).divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);
    }
}
