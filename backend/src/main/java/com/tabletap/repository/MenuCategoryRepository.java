package com.tabletap.repository;

import com.tabletap.domain.MenuCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MenuCategoryRepository extends JpaRepository<MenuCategory, Long> {
    List<MenuCategory> findByRestaurantIdOrderBySortOrderAscNameAsc(Long restaurantId);
}
