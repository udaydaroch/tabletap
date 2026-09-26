package com.tabletap.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter @Setter @NoArgsConstructor
public class CustomerOrder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private AppUser waiter;

    /** The table on the floor plan (null for walk-up / manually typed tables). */
    @ManyToOne(fetch = FetchType.LAZY)
    private FloorElement diningTable;

    /** Snapshot of the table's label at order time. */
    @Column(nullable = false)
    private String tableLabel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.SENT;

    private String notes;

    /**
     * Random id generated on the waiter's device. If a phone loses Wi-Fi and resends a queued
     * order, the server recognises it and returns the original instead of creating a duplicate.
     */
    @Column(length = 64, unique = true)
    private String clientRequestId;

    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<OrderLine> lines = new ArrayList<>();
}
