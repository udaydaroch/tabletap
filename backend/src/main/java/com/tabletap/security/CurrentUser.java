package com.tabletap.security;

import com.tabletap.domain.AppUser;
import com.tabletap.domain.Role;
import com.tabletap.repository.AppUserRepository;
import com.tabletap.service.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Resolves the logged-in (or impersonated) user from the JWT and re-validates it against the
 * database on every request, so disabling a user, changing a password or suspending an owner
 * takes effect immediately rather than when the token expires.
 */
@Component
@RequiredArgsConstructor
public class CurrentUser {
    private final AppUserRepository users;

    public AppUser get() {
        Jwt jwt = jwt();
        AppUser u = users.findWithRestaurantById(Long.valueOf(jwt.getSubject()))
            .orElseThrow(() -> ApiException.unauthorized("Account not found"));
        if (!u.isActive()) throw ApiException.unauthorized("This account has been disabled");
        Object ver = jwt.getClaim("ver");
        if (!(ver instanceof Number n) || n.intValue() != u.getTokenVersion())
            throw ApiException.unauthorized("Session expired, please sign in again");
        if (u.getRestaurant() != null && !u.getRestaurant().getOwner().isActive())
            throw ApiException.unauthorized("This restaurant's account is suspended");
        Long imp = impersonatorId();
        if (imp != null && users.findById(imp).filter(a -> a.isActive() && a.getRole() == Role.ADMIN).isEmpty())
            throw ApiException.unauthorized("Impersonation no longer valid");
        return u;
    }

    public Long impersonatorId() {
        String imp = jwt().getClaimAsString("imp");
        return imp == null ? null : Long.valueOf(imp);
    }

    private Jwt jwt() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) throw ApiException.unauthorized("Not logged in");
        return jwt;
    }
}
