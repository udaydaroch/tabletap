package com.tabletap.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** One part of a bill, paid one way (e.g. "Guest 2 of 3 — card"). */
@Entity
@Getter @Setter @NoArgsConstructor
public class Payment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Bill bill;

    @Column(nullable = false)
    private String label;

    /** CASH or CARD. */
    @Column(nullable = false)
    private String method;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(precision = 10, scale = 2)
    private BigDecimal tendered;

    @Column(precision = 10, scale = 2)
    private BigDecimal changeGiven;

    /** Card terminal receipt number, if entered. */
    private String reference;
}
