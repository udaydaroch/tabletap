package com.tabletap.repository;

import com.tabletap.domain.AppUser;
import com.tabletap.domain.Role;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByEmailIgnoreCase(String email);

    /** Loads the user with restaurant + owner so access checks work outside a transaction. */
    @EntityGraph(attributePaths = {"restaurant", "restaurant.owner"})
    Optional<AppUser> findWithRestaurantById(Long id);
    boolean existsByEmailIgnoreCase(String email);
    List<AppUser> findByRoleOrderByCreatedAtDesc(Role role);
    List<AppUser> findByRestaurantIdOrderByFullName(Long restaurantId);
}
