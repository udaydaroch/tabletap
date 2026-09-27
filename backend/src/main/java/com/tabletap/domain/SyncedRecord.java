package com.tabletap.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** (Cloud side) one change received from a site. (site, sourceId) is unique, so resends are ignored. */
@Entity
@Table(name = "synced_record", uniqueConstraints = @UniqueConstraint(columnNames = {"site_id", "sourceId"}))
@Getter @Setter @NoArgsConstructor
public class SyncedRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Site site;

    @Column(nullable = false)
    private Long sourceId;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    private Instant occurredAt;

    private Instant receivedAt = Instant.now();
}
