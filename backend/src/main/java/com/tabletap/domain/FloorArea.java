package com.tabletap.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** A room/zone of a restaurant's floor plan, e.g. "Main floor", "Patio", "Upstairs". */
@Entity
@Table(name = "floor_area")
@Getter @Setter @NoArgsConstructor
public class FloorArea {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Restaurant restaurant;

    @Column(nullable = false)
    private String name;

    private int sortOrder;

    /** Canvas size in layout units (the UI scales it to the screen). */
    private int width = 1000;
    private int height = 700;

    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "area", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<FloorElement> elements = new ArrayList<>();
}
