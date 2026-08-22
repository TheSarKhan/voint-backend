package com.starsoft.voint.outbound;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OutboundCampaignRepository extends JpaRepository<OutboundCampaign, UUID> {

    List<OutboundCampaign> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<OutboundCampaign> findByTenantIdAndId(UUID tenantId, UUID id);

    List<OutboundCampaign> findByStatus(String status);
}
