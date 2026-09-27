package com.tabletap.service;

import com.tabletap.domain.AppUser;
import com.tabletap.domain.Restaurant;
import com.tabletap.domain.OrderStatus;
import com.tabletap.domain.Role;
import com.tabletap.dto.RestaurantDtos.*;
import com.tabletap.live.LiveEvent;
import com.tabletap.repository.OrderRepository;
import com.tabletap.repository.RestaurantRepository;
import com.tabletap.repository.ShiftRepository;
import org.springframework.context.ApplicationEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class RestaurantService {
    private final RestaurantRepository restaurants;
    private final AccessService access;
    private final ShiftRepository shifts;
    private final OrderRepository orders;
    private final ApplicationEventPublisher events;

    private static final List<OrderStatus> OPEN = Arrays.stream(OrderStatus.values()).filter(OrderStatus::isOpen).toList();

    public Restaurant load(Long id) {
        return restaurants.findById(id).orElseThrow(() -> ApiException.notFound("Restaurant"));
    }

    @Transactional(readOnly = true)
    public List<RestaurantView> listFor(AppUser u) {
        List<Restaurant> list = switch (u.getRole()) {
            case ADMIN -> restaurants.findAllByOrderByName();
            case OWNER -> restaurants.findByOwnerIdOrderByName(u.getId());
            case WAITER, CHEF -> u.getRestaurant() == null ? List.of() : List.of(u.getRestaurant());
        };
        return list.stream().map(r -> RestaurantView.of(r,
            shifts.countByRestaurantIdAndClockOutIsNull(r.getId()),
            orders.countByRestaurantIdAndStatusIn(r.getId(), OPEN))).toList();
    }

    @Transactional(readOnly = true)
    public RestaurantView get(AppUser u, Long id) {
        Restaurant r = load(id);
        access.requireWork(u, r);
        return RestaurantView.of(r);
    }

    public RestaurantView create(AppUser u, RestaurantRequest req) {
        if (u.getRole() != Role.OWNER) throw ApiException.badRequest("Only restaurant owners can create restaurants (admins: log in as the owner)");
        Restaurant r = new Restaurant();
        r.setOwner(u);
        apply(r, req);
        restaurants.save(r);
        events.publishEvent(LiveEvent.of(LiveEvent.RESTAURANT_CHANGED, r, u.getId())); // also refreshes billing
        return RestaurantView.of(r);
    }

    public RestaurantView update(AppUser u, Long id, RestaurantRequest req) {
        Restaurant r = load(id);
        access.requireManage(u, r);
        apply(r, req);
        events.publishEvent(LiveEvent.of(LiveEvent.RESTAURANT_CHANGED, r, u.getId()));
        return RestaurantView.of(r);
    }

    private void apply(Restaurant r, RestaurantRequest req) {
        r.setName(req.name().trim());
        r.setAddress(req.address());
        r.setCuisine(req.cuisine());
        if (req.timeZone() != null && !req.timeZone().isBlank()) {
            try {
                r.setTimeZone(java.time.ZoneId.of(req.timeZone().trim()).getId());
            } catch (java.time.DateTimeException e) {
                throw ApiException.badRequest("Unknown time zone: " + req.timeZone());
            }
        }
    }
}
