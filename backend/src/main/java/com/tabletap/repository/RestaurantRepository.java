package com.tabletap.repository;

import com.tabletap.domain.Restaurant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {
    List<Restaurant> findByOwnerIdOrderByName(Long ownerId);
    List<Restaurant> findAllByOrderByName();
    long countByOwnerId(Long ownerId);
}
