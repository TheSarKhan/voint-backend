package com.starsoft.voint.rag;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RagDocumentRepository extends JpaRepository<RagDocument, UUID> {

    List<RagDocument> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<RagDocument> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * pgvector cosine-distance nearest-neighbour search, tenant-scoped. {@code embedding} is a
     * pgvector text literal (e.g. {@code "[0.01,0.02,...]"}, see {@link VectorUtils#toPgVector})
     * bound as a plain string and cast server-side - pgvector columns accept their text input
     * format via a normal {@code CAST(? AS vector)}, so no custom JDBC type is required.
     */
    @Query(value = "SELECT id, tenant_id, content, category, source, created_at FROM rag_documents "
            + "WHERE tenant_id = :tenantId AND embedding IS NOT NULL "
            + "ORDER BY embedding <=> CAST(:embedding AS vector) LIMIT :limit", nativeQuery = true)
    List<RagDocument> findNearestByTenant(@Param("tenantId") UUID tenantId,
                                           @Param("embedding") String embedding,
                                           @Param("limit") int limit);

    /** Embedding isn't JPA-mapped (see {@link RagDocument}), so it's written via native SQL. */
    @Modifying
    @Query(value = "UPDATE rag_documents SET embedding = CAST(:embedding AS vector) WHERE id = :id",
            nativeQuery = true)
    void updateEmbedding(@Param("id") UUID id, @Param("embedding") String embedding);

    /** Used by the startup backfill runner - rows left over from seed data / failed embed calls. */
    @Query(value = "SELECT id, tenant_id, content, category, source, created_at FROM rag_documents "
            + "WHERE embedding IS NULL", nativeQuery = true)
    List<RagDocument> findWithNullEmbedding();
}
