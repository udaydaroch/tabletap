package com.tabletap.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** One owner's monthly charge. (owner, month) is unique, so a month is never billed twice. */
@Entity
@Table(name = "billing_run", uniqueConstraints = @UniqueConstraint(columnNames = {"owner_id", "billingMonth"}))
@Getter @Setter @NoArgsConstructor
public class BillingRun {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private AppUser owner;

    /** e.g. 2026-09 */
    @Column(nullable = false, length = 7)
    private String billingMonth;

    @Column(precision = 10, scale = 2)
    private BigDecimal amount;

    /** CHARGED, FAILED or SKIPPED (nothing owed / no provider). */
    @Column(nullable = false)
    private String status;

    private String providerReference;

    private String error;

    private Instant createdAt = Instant.now();
}
