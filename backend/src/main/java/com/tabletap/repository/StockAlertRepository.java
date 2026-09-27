package com.tabletap.repository;

import com.tabletap.domain.StockAlert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockAlertRepository extends JpaRepository<StockAlert, Long> {
    List<StockAlert> findByRestaurantIdAndAcknowledgedFalseOrderByCreatedAtDesc(Long restaurantId);
    void deleteByIngredientId(Long ingredientId);
}
