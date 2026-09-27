package com.tabletap.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Snapshot of a menu item at order time, so later menu edits don't rewrite history. */
@Entity
@Getter @Setter @NoArgsConstructor
public class OrderLine {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private CustomerOrder order;

    private Long menuItemId;

    @Column(nullable = false)
    private String itemName;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    private int quantity;

    @ElementCollection
    @CollectionTable(name = "order_line_option")
    @Column(name = "label")
    private List<String> options = new ArrayList<>();

    private String note;

    /** Station this line was routed to when the order was placed. */
    private String station;

    /** Snapshot of the dish's kitchen-language name at order time. */
    private String kitchenName;
}
