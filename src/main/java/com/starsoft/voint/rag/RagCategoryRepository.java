package com.starsoft.voint.rag;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RagCategoryRepository extends JpaRepository<RagCategory, UUID> {

    List<RagCategory> findByTenantIdOrderByNameAsc(UUID tenantId);

    boolean existsByTenantIdAndNameIgnoreCase(UUID tenantId, String name);
}
