package com.tabletap.repository;

import com.tabletap.domain.Shift;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShiftRepository extends JpaRepository<Shift, Long> {
    Optional<Shift> findFirstByUserIdAndClockOutIsNull(Long userId);
    List<Shift> findTop20ByUserIdOrderByClockInDesc(Long userId);
    List<Shift> findByRestaurantIdAndClockOutIsNull(Long restaurantId);
    long countByRestaurantIdAndClockOutIsNull(Long restaurantId);
    List<Shift> findTop100ByRestaurantIdOrderByClockInDesc(Long restaurantId);
}
