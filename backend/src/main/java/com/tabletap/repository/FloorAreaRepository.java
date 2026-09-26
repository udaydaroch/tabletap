package com.tabletap.repository;

import com.tabletap.domain.FloorArea;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FloorAreaRepository extends JpaRepository<FloorArea, Long> {
    List<FloorArea> findByRestaurantIdOrderBySortOrderAscIdAsc(Long restaurantId);
    Optional<FloorArea> findFirstByRestaurantIdOrderByCreatedAtAsc(Long restaurantId);
}
