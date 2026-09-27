package com.tabletap.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** How much of an ingredient one portion of a dish uses. */
@Entity
@Table(name = "recipe_line", uniqueConstraints = @UniqueConstraint(columnNames = {"menu_item_id", "ingredient_id"}))
@Getter @Setter @NoArgsConstructor
public class RecipeLine {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private MenuItem menuItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Ingredient ingredient;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;
}
