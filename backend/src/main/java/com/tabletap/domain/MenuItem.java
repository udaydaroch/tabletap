package com.tabletap.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter @Setter @NoArgsConstructor
public class MenuItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private MenuCategory category;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    private boolean available = true;

    /** Set when stock tracking switched the item off, so restocking can switch it back on (manual 86s are left alone). */
    @Column(nullable = false, columnDefinition = "boolean default false") // safe to add to existing databases
    private boolean autoUnavailable;

    /** Overrides the category's station for this one dish. Null = use the category's. */
    private String station;

    /** Name in the kitchen's language, printed/shown under the menu name (e.g. for chefs who read Hindi). */
    private String kitchenName;

    /** Modifiers the waiter can tick, e.g. "Rare", "No onion", "Gluten free". */
    /** Ingredients one portion uses (optional — only needed for stock tracking). */
    @OneToMany(mappedBy = "menuItem", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RecipeLine> recipe = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "menu_item_option")
    @Column(name = "label")
    private List<String> options = new ArrayList<>();
}
