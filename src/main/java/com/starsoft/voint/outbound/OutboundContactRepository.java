package com.starsoft.voint.outbound;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OutboundContactRepository extends JpaRepository<OutboundContact, UUID> {

    List<OutboundContact> findByCampaignIdOrderByCreatedAtAsc(UUID campaignId);

    Page<OutboundContact> findByCampaignId(UUID campaignId, Pageable pageable);

    Optional<OutboundContact> findByTenantIdAndId(UUID tenantId, UUID id);

    long countByCampaignId(UUID campaignId);

    long countByCampaignIdAndStatus(UUID campaignId, String status);

    long countByCampaignIdAndCallOutcome(UUID campaignId, String callOutcome);

    @Query("""
            SELECT c FROM OutboundContact c
            WHERE c.campaignId = :campaignId
              AND (c.status = 'PENDING' OR (c.status IN ('NO_ANSWER', 'BUSY') AND c.retryCount < :maxRetries AND (c.nextAttemptAt IS NULL OR c.nextAttemptAt <= :now)))
            ORDER BY c.retryCount ASC, c.createdAt ASC
            """)
    List<OutboundContact> findNextDialableContacts(@Param("campaignId") UUID campaignId,
                                                  @Param("maxRetries") int maxRetries,
                                                  @Param("now") Instant now,
                                                  Pageable pageable);

    Optional<OutboundContact> findByTenantIdAndCallId(UUID tenantId, UUID callId);
}
