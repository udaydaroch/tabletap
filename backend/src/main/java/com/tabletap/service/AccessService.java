package com.tabletap.service;

import com.tabletap.domain.AppUser;
import com.tabletap.domain.Restaurant;
import com.tabletap.domain.Role;
import org.springframework.stereotype.Service;

/**
 * Central place for "who can do what". Admin inherits everything an owner and a waiter can do.
 */
@Service
public class AccessService {

    /** Owner-level actions: edit menu, manage staff, see shifts. */
    public boolean canManage(AppUser u, Restaurant r) {
        return u.getRole() == Role.ADMIN
            || (u.getRole() == Role.OWNER && r.getOwner().getId().equals(u.getId()));
    }

    /** Floor-level actions: read menu, take orders, view kitchen queue. */
    public boolean canWork(AppUser u, Restaurant r) {
        return canManage(u, r)
            || isStaffOf(u, r);
    }

    /** Waiters and chefs belong to exactly one restaurant. */
    public boolean isStaffOf(AppUser u, Restaurant r) {
        return (u.getRole() == Role.WAITER || u.getRole() == Role.CHEF)
            && u.getRestaurant() != null && u.getRestaurant().getId().equals(r.getId());
    }

    /** Taking orders: everyone who works there except kitchen staff. */
    public void requireFrontOfHouse(AppUser u, Restaurant r) {
        requireWork(u, r);
        if (u.getRole() == Role.CHEF) throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN, "Chefs can't take orders");
    }

    /** Marking dishes sold out: owners/admins and chefs. */
    public void requireKitchen(AppUser u, Restaurant r) {
        if (canManage(u, r) || (u.getRole() == Role.CHEF && isStaffOf(u, r))) return;
        throw ApiException.forbidden();
    }

    public void requireManage(AppUser u, Restaurant r) {
        if (!canManage(u, r)) throw ApiException.forbidden();
    }

    public void requireWork(AppUser u, Restaurant r) {
        if (!canWork(u, r)) throw ApiException.forbidden();
    }

    /** Can `viewer` see the profile of `target`? */
    public boolean canView(AppUser viewer, AppUser target) {
        if (viewer.getRole() == Role.ADMIN || viewer.getId().equals(target.getId())) return true;
        return target.getRestaurant() != null && canManage(viewer, target.getRestaurant());
    }
}
