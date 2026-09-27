package com.tabletap.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Transactional outbox (on the restaurant computer): a copy of each important change, written in the
 * same database transaction as the change itself, then sent to the cloud when the internet is up.
 */
@Entity
@Table(name = "sync_outbox", indexes = @Index(columnList = "sentAt"))
@Getter @Setter @NoArgsConstructor
public class SyncOutbox {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    private Instant createdAt = Instant.now();

    private Instant sentAt;

    private int attempts;

    private String lastError;
}
