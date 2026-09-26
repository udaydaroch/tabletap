package com.tabletap.service;

import com.tabletap.domain.AppUser;
import com.tabletap.domain.Restaurant;
import com.tabletap.domain.Role;
import com.tabletap.dto.TreeNode;
import com.tabletap.repository.AppUserRepository;
import com.tabletap.repository.RestaurantRepository;
import com.tabletap.repository.ShiftRepository;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/** Builds the clickable org chart: Platform -> Owners -> Restaurants -> Staff (managers first). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TreeService {
    private final AppUserRepository users;
    private final RestaurantRepository restaurants;
    private final ShiftRepository shifts;

    public TreeNode treeFor(AppUser u) {
        return switch (u.getRole()) {
            case ADMIN -> new TreeNode("platform", "PLATFORM", "TableTap platform", "All owners", null, true, false,
                users.findByRoleOrderByCreatedAtDesc(Role.OWNER).stream().map(this::ownerNode).toList());
            case OWNER -> ownerNode(u);
            case WAITER, CHEF -> throw ApiException.forbidden();
        };
    }

    private TreeNode ownerNode(AppUser owner) {
        List<TreeNode> kids = restaurants.findByOwnerIdOrderByName(owner.getId()).stream().map(this::restaurantNode).toList();
        return new TreeNode("u" + owner.getId(), "OWNER", owner.getFullName(), "Owner · " + owner.getEmail(),
            owner.getId(), owner.isActive(), false, kids);
    }

    private TreeNode restaurantNode(Restaurant r) {
        Set<Long> onShift = shifts.findByRestaurantIdAndClockOutIsNull(r.getId()).stream()
            .map(s -> s.getUser().getId()).collect(Collectors.toSet());
        List<TreeNode> staff = users.findByRestaurantIdOrderByFullName(r.getId()).stream()
            .sorted(Comparator.comparing((AppUser s) -> !"manager".equalsIgnoreCase(s.getTitle())))
            .map(s -> new TreeNode("u" + s.getId(), "STAFF", s.getFullName(),
                (s.getTitle() == null ? "Staff" : s.getTitle()) + " · " + s.getEmail(), s.getId(), s.isActive(), onShift.contains(s.getId()), List.of()))
            .toList();
        return new TreeNode("r" + r.getId(), "RESTAURANT", r.getName(),
            onShift.size() + "/" + staff.size() + " on shift" + (r.getCuisine() == null ? "" : " · " + r.getCuisine()),
            r.getId(), true, !onShift.isEmpty(), staff);
    }
}
