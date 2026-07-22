package com.starsoft.voint.call;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CallRepository extends JpaRepository<Call, UUID> {

    List<Call> findByTenantIdOrderByStartedAtDesc(UUID tenantId);

    Optional<Call> findByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    long countByTenantIdAndStatus(UUID tenantId, CallStatus status);

    @Query("select avg(c.durationSeconds) from Call c where c.tenantId = :tenantId and c.durationSeconds is not null")
    Double averageDurationSeconds(@Param("tenantId") UUID tenantId);
}
