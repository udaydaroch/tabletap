package com.tabletap.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** A network receipt printer in the kitchen or bar (ESC/POS over TCP, usually port 9100). */
@Entity
@Getter @Setter @NoArgsConstructor
public class Printer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Restaurant restaurant;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String host;

    private int port = 9100;

    /** Only print lines for this station. Null = print the whole order. */
    private String station;

    private boolean active = true;

    /** Send text as UTF-8 (needed for non-Latin kitchen names; most printers only take CP437). */
    private boolean utf8;

    private Instant lastPrintedAt;

    private String lastError;
}
