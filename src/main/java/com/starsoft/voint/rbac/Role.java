package com.starsoft.voint.rbac;

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

/**
 * A named set of permissions.
 *
 * <p>{@code tenantId == null} means the role belongs to the platform: either a role for Voint's
 * own staff, or a template a business can copy. A role with a tenant belongs to that business
 * and is invisible to everyone else.
 */
@Entity
@Table(name = "roles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Null = platform role or template. */
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(nullable = false)
    private String name;

    private String description;

    /** Offered to businesses as a starting point when they create their own roles. */
    @Column(name = "is_template", nullable = false)
    @Builder.Default
    private boolean template = false;

    /** Built in. Can be renamed, must not be deleted - the platform depends on it existing. */
    @Column(name = "is_system", nullable = false)
    @Builder.Default
    private boolean system = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
