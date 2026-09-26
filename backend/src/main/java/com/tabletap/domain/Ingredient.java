package com.tabletap.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** Something the kitchen keeps in stock (ribeye, fries, oat milk…). */
@Entity
@Getter @Setter @NoArgsConstructor
public class Ingredient {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Restaurant restaurant;

    @Column(nullable = false)
    private String name;

    /** Free text: "portions", "kg", "L"… */
    private String unit;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal stock = BigDecimal.ZERO;

    /** Alert the owner when stock falls to or below this. */
    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal lowThreshold = BigDecimal.ZERO;

    private Instant updatedAt = Instant.now();
}
