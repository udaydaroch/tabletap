package com.tabletap.service;

import com.tabletap.domain.AppUser;
import com.tabletap.domain.Restaurant;
import com.tabletap.domain.Role;
import com.tabletap.dto.ShiftDtos.ShiftView;
import com.tabletap.dto.UserDtos.*;
import com.tabletap.live.LiveEvent;
import com.tabletap.live.SessionRevoked;
import com.tabletap.repository.AppUserRepository;
import org.springframework.context.ApplicationEventPublisher;
import com.tabletap.repository.OrderRepository;
import com.tabletap.repository.ShiftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class StaffService {
    private final AppUserRepository users;
    private final ShiftRepository shifts;
    private final OrderRepository orders;
    private final RestaurantService restaurants;
    private final AccessService access;
    private final PasswordEncoder encoder;
    private final ApplicationEventPublisher events;

    public AppUser load(Long id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
    }

    @Transactional(readOnly = true)
    public List<UserView> list(AppUser u, Long restaurantId) {
        Restaurant r = restaurants.load(restaurantId);
        access.requireManage(u, r);
        return users.findByRestaurantIdOrderByFullName(restaurantId).stream().map(UserView::of).toList();
    }

    public UserView create(AppUser u, Long restaurantId, CreateStaffRequest req) {
        Restaurant r = restaurants.load(restaurantId);
        access.requireManage(u, r);
        Role role = req.role() == null ? Role.WAITER : req.role();
        if (role != Role.WAITER && role != Role.CHEF) throw ApiException.badRequest("Staff can be waiters or chefs"); // never OWNER/ADMIN
        String email = req.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) throw new ApiException(HttpStatus.CONFLICT, "Email already registered");
        AppUser w = new AppUser();
        w.setEmail(email);
        w.setFullName(req.fullName().trim());
        w.setPasswordHash(encoder.encode(req.password()));
        w.setRole(role);
        w.setTitle(req.title() == null || req.title().isBlank() ? (role == Role.CHEF ? "Chef" : "Waiter") : req.title().trim());
        w.setRestaurant(r);
        users.save(w);
        events.publishEvent(LiveEvent.of(LiveEvent.STAFF_CHANGED, r, w.getId()));
        return UserView.of(w);
    }

    public UserView update(AppUser u, Long staffId, UpdateStaffRequest req) {
        AppUser w = load(staffId);
        if (w.getRestaurant() == null) throw ApiException.badRequest("Not a staff member");
        access.requireManage(u, w.getRestaurant());
        if (req.fullName() != null && !req.fullName().isBlank()) w.setFullName(req.fullName().trim());
        if (req.title() != null) w.setTitle(req.title().trim());
        boolean revoke = false;
        if (req.active() != null && req.active() != w.isActive()) {
            w.setActive(req.active());
            revoke = !req.active();
            if (!req.active()) closeOpenShift(w);
        }
        if (req.newPassword() != null && !req.newPassword().isBlank()) {
            w.setPasswordHash(encoder.encode(req.newPassword()));
            revoke = true;
        }
        if (revoke) {
            w.setTokenVersion(w.getTokenVersion() + 1); // logs them out everywhere
            events.publishEvent(new SessionRevoked(w.getId(), false));
        }
        events.publishEvent(LiveEvent.of(LiveEvent.STAFF_CHANGED, w.getRestaurant(), w.getId()));
        return UserView.of(w);
    }

    /** A disabled staff member shouldn't stay "on shift" forever. */
    public void closeOpenShift(AppUser w) {
        shifts.findFirstByUserIdAndClockOutIsNull(w.getId()).ifPresent(s -> {
            s.setClockOut(Instant.now());
            events.publishEvent(LiveEvent.of(LiveEvent.SHIFT_CHANGED, s.getRestaurant(), w.getId()));
        });
    }

    @Transactional(readOnly = true)
    public UserDetail detail(AppUser viewer, Long userId) {
        AppUser t = load(userId);
        if (!access.canView(viewer, t)) throw ApiException.forbidden();
        Instant dayAgo = Instant.now().minus(24, ChronoUnit.HOURS);
        return new UserDetail(UserView.of(t),
            t.getRestaurant() == null ? null : t.getRestaurant().getName(),
            shifts.findFirstByUserIdAndClockOutIsNull(t.getId()).isPresent(),
            orders.countByWaiterIdAndCreatedAtAfter(t.getId(), dayAgo),
            shifts.findTop20ByUserIdOrderByClockInDesc(t.getId()).stream().map(ShiftView::of).toList());
    }
}
