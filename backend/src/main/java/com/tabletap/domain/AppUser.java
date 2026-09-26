package com.tabletap.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "app_user")
@Getter @Setter @NoArgsConstructor
public class AppUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    /** Free-text job title for staff, e.g. "Manager", "Head Waiter". */
    private String title;

    /** The restaurant a staff member works at. Null for owners and admins. */
    @ManyToOne(fetch = FetchType.LAZY)
    private Restaurant restaurant;

    private boolean active = true;

    /** Bumped on password change / disable; tokens carrying an older version are rejected. */
    @Column(nullable = false, columnDefinition = "integer default 0")
    private int tokenVersion;

    private Instant createdAt = Instant.now();
}
