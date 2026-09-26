package com.tabletap.service;

import com.tabletap.domain.*;
import com.tabletap.dto.OrderDtos.*;
import com.tabletap.live.LiveEvent;
import com.tabletap.repository.FloorElementRepository;
import com.tabletap.repository.MenuItemRepository;
import org.springframework.context.ApplicationEventPublisher;
import com.tabletap.repository.OrderRepository;
import com.tabletap.repository.ShiftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {
    private final OrderRepository orders;
    private final MenuItemRepository items;
    private final ShiftRepository shifts;
    private final FloorElementRepository tables;
    private final RestaurantService restaurants;
    private final AccessService access;
    private final ApplicationEventPublisher events;

    public OrderView create(AppUser u, Long restaurantId, CreateOrderRequest req) {
        Restaurant r = restaurants.load(restaurantId);
        access.requireFrontOfHouse(u, r);

        // offline retry of an order we already have -> return it, don't duplicate it
        if (req.clientRequestId() != null) {
            var existing = orders.findByClientRequestId(req.clientRequestId());
            if (existing.isPresent()) {
                if (!existing.get().getRestaurant().getId().equals(r.getId())) throw ApiException.badRequest("Duplicate request id");
                return OrderView.of(existing.get());
            }
        }
        if (u.getRole() == Role.WAITER && shifts.findFirstByUserIdAndClockOutIsNull(u.getId()).isEmpty())
            throw ApiException.badRequest("Clock in before taking orders");

        CustomerOrder o = new CustomerOrder();
        o.setRestaurant(r);
        o.setWaiter(u);
        if (req.tableId() != null) {
            FloorElement t = tables.findById(req.tableId()).orElseThrow(() -> ApiException.notFound("Table"));
            if (t.getKind() != FloorElement.Kind.TABLE || !t.getArea().getRestaurant().getId().equals(r.getId()))
                throw ApiException.badRequest("That table isn't in this restaurant");
            o.setDiningTable(t);
            o.setTableLabel(t.getLabel());
        } else if (req.tableLabel() != null && !req.tableLabel().isBlank()) {
            o.setTableLabel(req.tableLabel().trim());
        } else {
            throw ApiException.badRequest("Pick a table");
        }
        o.setNotes(req.notes());
        o.setClientRequestId(req.clientRequestId());

        for (LineRequest lr : req.lines()) {
            MenuItem item = items.findById(lr.menuItemId()).orElseThrow(() -> ApiException.notFound("Menu item"));
            if (!item.getCategory().getRestaurant().getId().equals(r.getId()))
                throw ApiException.badRequest(item.getName() + " is not on this restaurant's menu");
            if (!item.isAvailable()) throw ApiException.badRequest(item.getName() + " is currently unavailable");
            List<String> opts = lr.options() == null ? List.of() : lr.options();
            if (!item.getOptions().containsAll(opts))
                throw ApiException.badRequest("Invalid option for " + item.getName());

            OrderLine line = new OrderLine();
            line.setOrder(o);
            line.setMenuItemId(item.getId());
            line.setItemName(item.getName());
            line.setUnitPrice(item.getPrice());
            line.setQuantity(lr.quantity());
            line.getOptions().addAll(opts);
            line.setNote(lr.note());
            o.getLines().add(line);
        }
        orders.save(o);
        // Kitchen screens (and later a docket-printer service) react to this event.
        events.publishEvent(LiveEvent.of(LiveEvent.ORDER_CREATED, r, u.getId()));
        return OrderView.of(o);
    }

    @Transactional(readOnly = true)
    public List<OrderView> list(AppUser u, Long restaurantId, boolean openOnly) {
        Restaurant r = restaurants.load(restaurantId);
        access.requireWork(u, r);
        List<CustomerOrder> list = openOnly
            ? orders.findByRestaurantIdAndStatusInOrderByCreatedAtAsc(restaurantId,
                Arrays.stream(OrderStatus.values()).filter(OrderStatus::isOpen).toList())
            : orders.findTop50ByRestaurantIdOrderByCreatedAtDesc(restaurantId);
        return list.stream().map(OrderView::of).toList();
    }

    public OrderView updateStatus(AppUser u, Long orderId, OrderStatus status) {
        CustomerOrder o = orders.findById(orderId).orElseThrow(() -> ApiException.notFound("Order"));
        access.requireWork(u, o.getRestaurant());
        if (!o.getStatus().canMoveTo(status))
            throw ApiException.badRequest("Can't move an order from " + o.getStatus() + " to " + status);
        o.setStatus(status);
        events.publishEvent(LiveEvent.of(LiveEvent.ORDER_UPDATED, o.getRestaurant(), u.getId()));
        return OrderView.of(o);
    }
}
