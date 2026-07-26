package com.starsoft.voint.usage;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsageEventRepository extends JpaRepository<UsageEvent, UUID> {

    /** AI totals for one tenant over a half-open time window [from, to). */
    @Query("""
            select coalesce(sum(u.promptTokens), 0)     as promptTokens,
                   coalesce(sum(u.completionTokens), 0) as completionTokens,
                   coalesce(sum(u.ttsCharacters), 0)    as ttsCharacters
            from UsageEvent u
            where u.tenantId = :tenantId
              and u.occurredAt >= :from
              and u.occurredAt < :to
            """)
    AiTotals sumForTenant(@Param("tenantId") UUID tenantId,
                          @Param("from") Instant from,
                          @Param("to") Instant to);

    /** Same totals for every tenant that had any usage in the window, in one round trip. */
    @Query("""
            select u.tenantId                           as tenantId,
                   coalesce(sum(u.promptTokens), 0)     as promptTokens,
                   coalesce(sum(u.completionTokens), 0) as completionTokens,
                   coalesce(sum(u.ttsCharacters), 0)    as ttsCharacters
            from UsageEvent u
            where u.occurredAt >= :from
              and u.occurredAt < :to
            group by u.tenantId
            """)
    List<TenantAiTotals> sumGroupedByTenant(@Param("from") Instant from, @Param("to") Instant to);

    /** Projection: SUM over an empty set still yields 0 thanks to the COALESCE above. */
    interface AiTotals {
        Long getPromptTokens();

        Long getCompletionTokens();

        Long getTtsCharacters();
    }

    interface TenantAiTotals extends AiTotals {
        UUID getTenantId();
    }
}
