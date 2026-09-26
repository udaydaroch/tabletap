package com.tabletap.dto;

import com.tabletap.domain.MenuCategory;
import com.tabletap.domain.MenuItem;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public final class MenuDtos {
    private MenuDtos() {}

    public record CategoryRequest(@NotBlank @Size(max = 60) String name, Integer sortOrder) {}

    public record ItemRequest(@NotNull Long categoryId, @NotBlank @Size(max = 100) String name,
                              @Size(max = 1000) String description,
                              @NotNull @DecimalMin("0.00") @DecimalMax("100000.00") BigDecimal price, Boolean available,
                              @Size(max = 30) List<@Size(max = 60) String> options) {}

    public record ItemView(Long id, Long categoryId, String name, String description, BigDecimal price,
                           boolean available, List<String> options) {
        public static ItemView of(MenuItem i) {
            return new ItemView(i.getId(), i.getCategory().getId(), i.getName(), i.getDescription(),
                i.getPrice(), i.isAvailable(), List.copyOf(i.getOptions()));
        }
    }

    public record CategoryView(Long id, String name, int sortOrder, List<ItemView> items) {
        public static CategoryView of(MenuCategory c) {
            return new CategoryView(c.getId(), c.getName(), c.getSortOrder(),
                c.getItems().stream().map(ItemView::of).toList());
        }
    }
}
