package com.tabletap.web;

import com.tabletap.dto.DayReport;
import com.tabletap.dto.OrderDtos.*;
import com.tabletap.security.CurrentUser;
import com.tabletap.service.OrderService;
import com.tabletap.service.ReportService;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orders;
    private final ReportService reports;
    private final CurrentUser current;

    @PostMapping("/restaurants/{rid}/orders")
    public OrderView send(@PathVariable Long rid, @Valid @RequestBody CreateOrderRequest req) {
        return orders.create(current.get(), rid, req);
    }

    @GetMapping("/restaurants/{rid}/orders")
    public List<OrderView> list(@PathVariable Long rid, @RequestParam(defaultValue = "true") boolean open) {
        return orders.list(current.get(), rid, open);
    }

    /** Full order history for one day (defaults to today in the caller's time zone). */
    @GetMapping("/restaurants/{rid}/orders/day")
    public DayReport day(@PathVariable Long rid,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                         @RequestParam(required = false) String tz) {
        return reports.day(current.get(), rid, date, tz);
    }

    @PatchMapping("/orders/{id}/status")
    public OrderView status(@PathVariable Long id, @Valid @RequestBody StatusRequest req) {
        return orders.updateStatus(current.get(), id, req.status());
    }
}
