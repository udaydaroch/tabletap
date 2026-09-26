package com.tabletap.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** (Cloud side) a restaurant computer allowed to send its data here. Only a hash of its key is stored. */
@Entity
@Getter @Setter @NoArgsConstructor
public class Site {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String keyHash;

    private Instant createdAt = Instant.now();

    private Instant lastSyncAt;

    private long recordCount;
}
