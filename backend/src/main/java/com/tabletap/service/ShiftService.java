package com.tabletap.service;

import com.tabletap.domain.AppUser;
import com.tabletap.domain.Restaurant;
import com.tabletap.domain.Shift;
import com.tabletap.dto.ShiftDtos.*;
import com.tabletap.live.LiveEvent;
import com.tabletap.repository.ShiftRepository;
import org.springframework.context.ApplicationEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ShiftService {
    private final ShiftRepository shifts;
    private final RestaurantService restaurants;
    private final AccessService access;
    private final ApplicationEventPublisher events;

    public ShiftView clockIn(AppUser u) {
        if (u.getRestaurant() == null) throw ApiException.badRequest("Only staff members clock in");
        if (shifts.findFirstByUserIdAndClockOutIsNull(u.getId()).isPresent()) throw ApiException.badRequest("Already clocked in");
        Shift s = new Shift();
        s.setUser(u);
        s.setRestaurant(u.getRestaurant());
        s.setClockIn(Instant.now());
        shifts.save(s);
        events.publishEvent(LiveEvent.of(LiveEvent.SHIFT_CHANGED, u.getRestaurant(), u.getId()));
        return ShiftView.of(s);
    }

    public ShiftView clockOut(AppUser u) {
        Shift s = shifts.findFirstByUserIdAndClockOutIsNull(u.getId())
            .orElseThrow(() -> ApiException.badRequest("Not clocked in"));
        s.setClockOut(Instant.now());
        events.publishEvent(LiveEvent.of(LiveEvent.SHIFT_CHANGED, s.getRestaurant(), u.getId()));
        return ShiftView.of(s);
    }

    @Transactional(readOnly = true)
    public MyShifts mine(AppUser u) {
        return new MyShifts(
            shifts.findFirstByUserIdAndClockOutIsNull(u.getId()).map(ShiftView::of).orElse(null),
            shifts.findTop20ByUserIdOrderByClockInDesc(u.getId()).stream().map(ShiftView::of).toList());
    }

    @Transactional(readOnly = true)
    public List<ShiftView> forRestaurant(AppUser u, Long restaurantId) {
        Restaurant r = restaurants.load(restaurantId);
        access.requireManage(u, r);
        return shifts.findTop100ByRestaurantIdOrderByClockInDesc(restaurantId).stream().map(ShiftView::of).toList();
    }
}
