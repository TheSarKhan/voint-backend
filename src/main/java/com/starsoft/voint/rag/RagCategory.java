package com.starsoft.voint.rag;

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
 * A custom knowledge-base heading a tenant added itself, beyond the built-in topic list the
 * panel ships with. Independent of {@link RagDocument} - a heading can exist with zero
 * documents filed under it yet (the whole point: it shows up as "not done" on the completion
 * checklist until someone fills it in).
 */
@Entity
@Table(name = "rag_categories")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
