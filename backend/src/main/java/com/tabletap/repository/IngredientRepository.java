package com.tabletap.repository;

import com.tabletap.domain.Ingredient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IngredientRepository extends JpaRepository<Ingredient, Long> {
    List<Ingredient> findByRestaurantIdOrderByName(Long restaurantId);
}
