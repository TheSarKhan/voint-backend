package com.starsoft.voint.rag;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface RagDocumentRepository extends JpaRepository<RagDocument, UUID> {

    List<RagDocument> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<RagDocument> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * pgvector cosine-distance nearest-neighbour search, tenant-scoped. {@code embedding} is a
     * pgvector text literal (e.g. {@code "[0.01,0.02,...]"}, see {@link VectorUtils#toPgVector})
     * bound as a plain string and cast server-side - pgvector columns accept their text input
     * format via a normal {@code CAST(? AS vector)}, so no custom JDBC type is required.
     *
     * <p>{@code maxDistance} (see {@link VectorUtils#MAX_COSINE_DISTANCE}) drops matches too weak
     * to be useful instead of always forcing the top-{@code limit} through - a query with no
     * relevant chunk in the knowledge base can legitimately return fewer rows, or none.
     */
    @Query(value = "SELECT id, tenant_id, content, category, source, active, hit_count, last_used_at, created_at "
            + "FROM rag_documents "
            + "WHERE tenant_id = :tenantId AND active = true AND embedding IS NOT NULL "
            + "AND embedding <=> CAST(:embedding AS vector) <= :maxDistance "
            + "ORDER BY embedding <=> CAST(:embedding AS vector) LIMIT :limit", nativeQuery = true)
    List<RagDocument> findNearestByTenant(@Param("tenantId") UUID tenantId,
                                           @Param("embedding") String embedding,
                                           @Param("maxDistance") double maxDistance,
                                           @Param("limit") int limit);

    /** Embedding isn't JPA-mapped (see {@link RagDocument}), so it's written via native SQL. */
    @Modifying
    @Query(value = "UPDATE rag_documents SET embedding = CAST(:embedding AS vector) WHERE id = :id",
            nativeQuery = true)
    void updateEmbedding(@Param("id") UUID id, @Param("embedding") String embedding);

    /** Used by the startup backfill runner - rows left over from seed data / failed embed calls. */
    @Query(value = "SELECT id, tenant_id, content, category, source, active, hit_count, last_used_at, created_at "
            + "FROM rag_documents WHERE embedding IS NULL", nativeQuery = true)
    List<RagDocument> findWithNullEmbedding();

    /**
     * Bumped every time a chunk gets pulled into a real call's context (see
     * VoiceWebhookService#ragSearch) - the completion checklist's counterpart: not just "does this
     * topic have something written", but "is the agent actually reaching for it".
     */
    @Modifying
    @Transactional
    @Query("UPDATE RagDocument d SET d.hitCount = d.hitCount + 1, d.lastUsedAt = CURRENT_TIMESTAMP WHERE d.id IN :ids")
    void recordHits(@Param("ids") List<UUID> ids);
}
