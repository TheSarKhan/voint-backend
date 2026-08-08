package com.starsoft.voint.approval;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, UUID> {

    List<ApprovalRequest> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<ApprovalRequest> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, String status);

    long countByTenantIdAndStatus(UUID tenantId, String status);

    /** Platform-wide, for the admin dashboard - how many businesses have something waiting on us. */
    long countByStatus(String status);

    /** The replay presents its one-shot secret; nothing else identifies it. */
    Optional<ApprovalRequest> findByReplayNonce(String replayNonce);
}
