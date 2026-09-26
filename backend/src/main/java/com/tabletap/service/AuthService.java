package com.tabletap.service;

import com.tabletap.domain.AppUser;
import com.tabletap.domain.Restaurant;
import com.tabletap.domain.Role;
import com.tabletap.dto.AuthDtos.*;
import com.tabletap.live.LiveEvent;
import com.tabletap.repository.AppUserRepository;
import com.tabletap.repository.RestaurantRepository;
import com.tabletap.security.LoginThrottle;
import com.tabletap.security.TokenService;
import jakarta.annotation.PostConstruct;
import org.springframework.context.ApplicationEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final AppUserRepository users;
    private final RestaurantRepository restaurants;
    private final PasswordEncoder encoder;
    private final TokenService tokens;
    private final LoginThrottle throttle;
    private final ApplicationEventPublisher events;
    private String dummyHash;

    @PostConstruct
    void init() {
        dummyHash = encoder.encode("timing-equaliser");
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req, String ip) {
        String email = req.email().trim().toLowerCase();
        throttle.checkLogin(email, ip);
        AppUser u = users.findByEmailIgnoreCase(email).orElse(null);
        // always run bcrypt so response time doesn't reveal whether the email exists
        boolean ok = encoder.matches(req.password(), u == null ? dummyHash : u.getPasswordHash());
        if (u == null || !ok) {
            throttle.loginFailed(email, ip);
            throw ApiException.unauthorized("Invalid email or password");
        }
        if (!u.isActive()) throw ApiException.unauthorized("This account has been disabled");
        if (u.getRestaurant() != null && !u.getRestaurant().getOwner().isActive())
            throw ApiException.unauthorized("This restaurant's account is suspended");
        throttle.loginSucceeded(email);
        return new AuthResponse(tokens.issue(u, null), me(u, null));
    }

    /** Self-service SaaS sign-up: creates an OWNER account (and optionally their first restaurant). */
    @Transactional
    public AuthResponse registerOwner(RegisterOwnerRequest req, String ip) {
        throttle.checkSignup(ip);
        String email = req.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) throw new ApiException(HttpStatus.CONFLICT, "Email already registered");
        AppUser owner = new AppUser();
        owner.setEmail(email);
        owner.setFullName(req.fullName().trim());
        owner.setPasswordHash(encoder.encode(req.password()));
        owner.setRole(Role.OWNER);
        users.save(owner);
        if (req.restaurantName() != null && !req.restaurantName().isBlank()) {
            Restaurant r = new Restaurant();
            r.setName(req.restaurantName().trim());
            r.setOwner(owner);
            restaurants.save(r);
            events.publishEvent(LiveEvent.of(LiveEvent.RESTAURANT_CHANGED, r, owner.getId()));
        }
        events.publishEvent(LiveEvent.owner(owner.getId()));
        return new AuthResponse(tokens.issue(owner, null), me(owner, null));
    }

    public Me me(AppUser u, Long impersonatorId) {
        Restaurant r = u.getRestaurant();
        return new Me(u.getId(), u.getEmail(), u.getFullName(), u.getRole(), u.getTitle(),
            r == null ? null : r.getId(), r == null ? null : r.getName(), impersonatorId);
    }
}
