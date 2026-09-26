package com.tabletap.service;

import com.tabletap.domain.AppUser;
import com.tabletap.domain.CustomerOrder;
import com.tabletap.domain.OrderStatus;
import com.tabletap.domain.Restaurant;
import com.tabletap.dto.DayReport;
import com.tabletap.dto.DayReport.ItemTotal;
import com.tabletap.dto.DayReport.Summary;
import com.tabletap.dto.DayReport.WaiterTotal;
import com.tabletap.dto.OrderDtos.OrderView;
import com.tabletap.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Order history grouped by calendar day in the restaurant's local time zone. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {
    private final OrderRepository orders;
    private final RestaurantService restaurants;
    private final AccessService access;

    public DayReport day(AppUser u, Long restaurantId, LocalDate date, String tz) {
        Restaurant r = restaurants.load(restaurantId);
        access.requireWork(u, r);
        ZoneId zone = zone(tz);
        LocalDate day = date == null ? LocalDate.now(zone) : date;

        List<OrderView> list = orders.findByRestaurantIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
                restaurantId, day.atStartOfDay(zone).toInstant(), day.plusDays(1).atStartOfDay(zone).toInstant())
            .stream().map(OrderView::of).toList();

        Summary summary = access.canManage(u, r) ? summarise(list) : null;
        return new DayReport(r.getId(), r.getName(), day, zone.getId(), list, summary);
    }

    private Summary summarise(List<OrderView> list) {
        List<OrderView> billable = list.stream().filter(o -> o.status() != OrderStatus.CANCELLED).toList();
        BigDecimal revenue = billable.stream().map(OrderView::total).reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, long[]> itemQty = new LinkedHashMap<>();
        Map<String, BigDecimal> itemRev = new LinkedHashMap<>();
        Map<String, long[]> waiterCount = new LinkedHashMap<>();
        Map<String, BigDecimal> waiterRev = new LinkedHashMap<>();
        for (OrderView o : billable) {
            waiterCount.computeIfAbsent(o.waiterName(), k -> new long[1])[0]++;
            waiterRev.merge(o.waiterName(), o.total(), BigDecimal::add);
            for (var l : o.lines()) {
                itemQty.computeIfAbsent(l.itemName(), k -> new long[1])[0] += l.quantity();
                itemRev.merge(l.itemName(), l.unitPrice().multiply(BigDecimal.valueOf(l.quantity())), BigDecimal::add);
            }
        }
        List<ItemTotal> items = itemQty.entrySet().stream()
            .map(e -> new ItemTotal(e.getKey(), e.getValue()[0], itemRev.get(e.getKey())))
            .sorted(Comparator.comparingLong(ItemTotal::quantity).reversed()).toList();
        List<WaiterTotal> waiters = waiterCount.entrySet().stream()
            .map(e -> new WaiterTotal(e.getKey(), e.getValue()[0], waiterRev.get(e.getKey())))
            .sorted(Comparator.comparing(WaiterTotal::revenue).reversed()).toList();

        long served = list.stream().filter(o -> o.status() == OrderStatus.SERVED).count();
        long cancelled = list.size() - billable.size();
        BigDecimal avg = billable.isEmpty() ? BigDecimal.ZERO
            : revenue.divide(BigDecimal.valueOf(billable.size()), 2, RoundingMode.HALF_UP);
        return new Summary(list.size(), served, billable.size() - served, cancelled, revenue, avg, items, waiters);
    }

    private ZoneId zone(String tz) {
        if (tz == null || tz.isBlank()) return ZoneId.of("UTC");
        try {
            return ZoneId.of(tz);
        } catch (DateTimeException e) {
            throw ApiException.badRequest("Unknown time zone");
        }
    }
}
