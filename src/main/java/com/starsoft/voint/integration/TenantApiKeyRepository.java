package com.starsoft.voint.integration;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TenantApiKeyRepository extends JpaRepository<TenantApiKey, UUID> {

    List<TenantApiKey> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<TenantApiKey> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<TenantApiKey> findByKeyHashAndActiveTrue(String keyHash);
}
