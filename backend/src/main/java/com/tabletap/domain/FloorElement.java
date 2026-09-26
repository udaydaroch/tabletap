package com.tabletap.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Something drawn on the floor plan: a table (orders can be placed on it) or a fixture
 * (bar, wall, kitchen, plant, …). Position/size are in the area's layout units.
 */
@Entity
@Table(name = "floor_element")
@Getter @Setter @NoArgsConstructor
public class FloorElement {
    public enum Kind { TABLE, FIXTURE }

    /** ARC = quarter circle (curved wall, or curved booth when it's a table). */
    public enum Shape { ROUND, SQUARE, RECTANGLE, TRIANGLE, HEXAGON, POLYGON, ARC }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private FloorArea area;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Kind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Shape shape;

    private String label;

    private double x;
    private double y;
    private double w;
    private double h;
    private double rotation;

    private int seats;

    /** POLYGON only: "x,y x,y …" with coordinates normalised to 0..1 of the element's box. */
    @Column(length = 2000)
    private String points;
}
