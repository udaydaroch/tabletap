package com.tabletap.dto;

import com.tabletap.domain.Ingredient;
import com.tabletap.domain.RecipeLine;
import com.tabletap.domain.StockAlert;
import com.tabletap.stock.StockRules;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class StockDtos {
    private StockDtos() {}

    public record IngredientRequest(@NotBlank @Size(max = 60) String name, @Size(max = 20) String unit,
                                    @DecimalMin("0") @DecimalMax("1000000") BigDecimal stock,
                                    @NotNull @DecimalMin("0") @DecimalMax("1000000") BigDecimal lowThreshold) {}

    public record RestockRequest(@NotNull @DecimalMin("-1000000") @DecimalMax("1000000") BigDecimal amount) {}

    public record IngredientView(Long id, String name, String unit, BigDecimal stock, BigDecimal lowThreshold, boolean low) {
        public static IngredientView of(Ingredient i) {
            return new IngredientView(i.getId(), i.getName(), i.getUnit(), i.getStock(), i.getLowThreshold(),
                StockRules.LOW.isSatisfiedBy(i));
        }
    }

    public record RecipeLineRequest(@NotNull Long ingredientId, @NotNull @DecimalMin("0.001") @DecimalMax("10000") BigDecimal quantity) {}

    public record RecipeRequest(@NotNull @Size(max = 30) @Valid List<RecipeLineRequest> lines) {}

    public record RecipeLineView(Long ingredientId, String ingredientName, String unit, BigDecimal quantity) {
        public static RecipeLineView of(RecipeLine l) {
            return new RecipeLineView(l.getIngredient().getId(), l.getIngredient().getName(), l.getIngredient().getUnit(), l.getQuantity());
        }
    }

    public record AlertView(Long id, String message, Instant createdAt) {
        public static AlertView of(StockAlert a) {
            return new AlertView(a.getId(), a.getMessage(), a.getCreatedAt());
        }
    }
}
