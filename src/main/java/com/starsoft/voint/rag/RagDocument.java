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
 * One RAG knowledge chunk belonging to a tenant.
 *
 * NOTE on the embedding column: the DB table has an `embedding vector(768)` column
 * (pgvector, see V1__init.sql), but it is intentionally NOT mapped on this entity
 * at the bootstrap stage - Hibernate has no native vector type here, and embeddings
 * are written/queried via native SQL when the real RAG pipeline lands
 * (INSERT ... embedding = ?::vector / ORDER BY embedding <=> ?::vector).
 */
@Entity
@Table(name = "rag_documents")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    /** e.g. "pricing", "working-hours", "delivery", "deposit". */
    private String category;

    /** Where the chunk came from (manual entry, file name, URL...). */
    private String source;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
