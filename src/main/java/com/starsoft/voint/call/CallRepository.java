package com.starsoft.voint.call;

import java.time.Instant;
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

    long countByTenantIdAndCallerNumber(UUID tenantId, String callerNumber);

    @Query("select avg(c.durationSeconds) from Call c where c.tenantId = :tenantId and c.durationSeconds is not null")
    Double averageDurationSeconds(@Param("tenantId") UUID tenantId);

    /** Billable call volume for one tenant over a half-open window [from, to). */
    @Query("""
            select count(c)                              as callCount,
                   coalesce(sum(c.durationSeconds), 0)   as totalSeconds
            from Call c
            where c.tenantId = :tenantId
              and c.startedAt >= :from
              and c.startedAt < :to
            """)
    CallTotals sumForTenant(@Param("tenantId") UUID tenantId,
                            @Param("from") Instant from,
                            @Param("to") Instant to);

    /** Same volume for every tenant that had calls in the window, in one round trip. */
    @Query("""
            select c.tenantId                            as tenantId,
                   count(c)                              as callCount,
                   coalesce(sum(c.durationSeconds), 0)   as totalSeconds
            from Call c
            where c.startedAt >= :from
              and c.startedAt < :to
            group by c.tenantId
            """)
    List<TenantCallTotals> sumGroupedByTenant(@Param("from") Instant from, @Param("to") Instant to);

    interface CallTotals {
        Long getCallCount();

        Long getTotalSeconds();
    }

    interface TenantCallTotals extends CallTotals {
        UUID getTenantId();
    }
}
