package com.starsoft.voint.auth;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A user of the business admin panel (per-tenant). */
@Entity
@Table(name = "panel_users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PanelUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Nullable: a platform-wide SUPER_ADMIN isn't scoped to any single tenant. */
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /**
     * Legacy string role. Kept because the JWT and TenantAccessGuard still read it; the real
     * permissions now live behind {@link #roleId}. Removing it in the same change that
     * introduces roles would have meant rewriting authentication and authorisation at once.
     */
    @Column(nullable = false)
    @Builder.Default
    private String role = "ADMIN";

    /** The role whose permission matrix applies to this user. */
    @Column(name = "role_id")
    private UUID roleId;

    /** ACTIVE / BLOCKED. Blocking keeps the account and its history; deleting orphans both. */
    @Column(nullable = false)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
