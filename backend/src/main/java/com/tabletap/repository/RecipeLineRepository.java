package com.tabletap.repository;

import com.tabletap.domain.RecipeLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface RecipeLineRepository extends JpaRepository<RecipeLine, Long> {
    List<RecipeLine> findByIngredientIdIn(Collection<Long> ingredientIds);
    List<RecipeLine> findByMenuItemIdIn(Collection<Long> menuItemIds);
    void deleteByIngredientId(Long ingredientId);
}
