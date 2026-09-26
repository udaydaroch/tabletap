package com.tabletap.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** A settled table: which orders were paid, how the bill was split and how each part was paid. */
@Entity
@Getter @Setter @NoArgsConstructor
public class Bill {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Restaurant restaurant;

    @Column(nullable = false)
    private String tableLabel;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    /** FULL, EVEN, BY_ITEM or CUSTOM. */
    @Column(nullable = false)
    private String splitStrategy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private AppUser closedBy;

    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "bill", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<Payment> payments = new ArrayList<>();
}
