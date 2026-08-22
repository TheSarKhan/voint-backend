package com.starsoft.voint.catalog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CatalogItemRepository extends JpaRepository<CatalogItem, UUID> {

    List<CatalogItem> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<CatalogItem> findByTenantIdAndActiveTrueOrderByCategoryAscNameAsc(UUID tenantId);

    Optional<CatalogItem> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<CatalogItem> findByTenantIdAndSku(UUID tenantId, String sku);

    @Query(value = """
            SELECT id, tenant_id, sku, name, category, item_type, price, price_daily, price_monthly, price_hourly,
                   deposit, currency, unit, duration_minutes, in_stock, stock_quantity, specs, description, active, created_at, updated_at
            FROM catalog_items
            WHERE tenant_id = :tenantId
              AND active = true
              AND embedding IS NOT NULL
            ORDER BY embedding <=> CAST(:embedding AS vector)
            LIMIT :limit
            """, nativeQuery = true)
    List<CatalogItem> findSimilar(@Param("tenantId") UUID tenantId,
                                 @Param("embedding") String embedding,
                                 @Param("limit") int limit);

    @Modifying
    @Query(value = "UPDATE catalog_items SET embedding = CAST(:embedding AS vector) WHERE id = :id", nativeQuery = true)
    void updateEmbedding(@Param("id") UUID id, @Param("embedding") String embedding);

    long countByTenantIdAndActiveTrue(UUID tenantId);
}
