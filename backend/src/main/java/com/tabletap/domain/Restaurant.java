package com.tabletap.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter @Setter @NoArgsConstructor
public class Restaurant {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String address;

    private String cuisine;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private AppUser owner;

    private Instant createdAt = Instant.now();

    public enum FloorPlanTier { BASIC, ADVANCED }

    /** BASIC = no floor plan (free). ADVANCED = floor-plan designer, a paid monthly add-on. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20) default 'BASIC'")
    private FloorPlanTier floorPlanTier = FloorPlanTier.BASIC;

    /** When the paid add-on started (billing is prorated from this day). */
    private Instant floorPlanAdvancedSince;
}
