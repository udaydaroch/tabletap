package com.tabletap.service;

import com.tabletap.domain.AppUser;
import com.tabletap.domain.Role;
import com.tabletap.dto.AuthDtos.AuthResponse;
import com.tabletap.dto.UserDtos.OwnerSummary;
import com.tabletap.dto.UserDtos.UserView;
import com.tabletap.live.LiveEvent;
import com.tabletap.live.SessionRevoked;
import com.tabletap.repository.AppUserRepository;
import org.springframework.context.ApplicationEventPublisher;
import com.tabletap.repository.RestaurantRepository;
import com.tabletap.security.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AdminService {
    private final AppUserRepository users;
    private final RestaurantRepository restaurants;
    private final TokenService tokens;
    private final AuthService auth;
    private final BillingService billing;
    private final StaffService staff;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public List<OwnerSummary> owners() {
        return users.findByRoleOrderByCreatedAtDesc(Role.OWNER).stream()
            .map(o -> new OwnerSummary(o.getId(), o.getFullName(), o.getEmail(), o.isActive(),
                restaurants.countByOwnerId(o.getId()), o.getCreatedAt(), billing.usageFor(o).estimatedTotal()))
            .toList();
    }

    /** "Log in as" — issues a token for the target user, stamped with the admin's id for auditing. */
    public AuthResponse impersonate(AppUser admin, Long userId) {
        AppUser target = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        if (target.getRole() == Role.ADMIN) throw ApiException.badRequest("Cannot impersonate another admin");
        if (!target.isActive()) throw ApiException.badRequest("Account is disabled");
        log.warn("AUDIT admin {} impersonating user {} ({})", admin.getId(), target.getId(), target.getEmail());
        return new AuthResponse(tokens.issue(target, admin.getId()), auth.me(target, admin.getId()));
    }

    public UserView setActive(Long userId, boolean active) {
        AppUser u = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        if (u.getRole() == Role.ADMIN) throw ApiException.badRequest("Cannot disable an admin");
        if (u.isActive() == active) return UserView.of(u);
        u.setActive(active);
        if (!active) {
            u.setTokenVersion(u.getTokenVersion() + 1);
            if (u.getRestaurant() != null) staff.closeOpenShift(u);
            // suspending an owner also cuts off live streams of all their staff
            events.publishEvent(new SessionRevoked(u.getId(), u.getRole() == Role.OWNER));
        }
        events.publishEvent(u.getRole() == Role.OWNER ? LiveEvent.owner(u.getId())
            : LiveEvent.of(LiveEvent.STAFF_CHANGED, u.getRestaurant(), u.getId()));
        return UserView.of(u);
    }
}
