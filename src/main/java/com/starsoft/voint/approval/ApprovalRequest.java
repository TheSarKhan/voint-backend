package com.starsoft.voint.approval;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A change somebody asked to make, waiting for a second pair of eyes.
 *
 * <p>The stored thing is the HTTP request itself, not a description of what it would do. Approving
 * replays it, so the operation runs through exactly the same controller, validation and permission
 * checks as an unheld one. The alternative - recording an intent and writing a second executor for
 * each kind of operation - means every rule exists twice, and the copies drift.
 */
@Entity
@Table(name = "approval_requests")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "requested_by")
    private UUID requestedBy;

    /** Frozen: the account may later be renamed or deleted, the record must still say who asked. */
    @Column(name = "requested_by_email", nullable = false)
    private String requestedByEmail;

    @Column(nullable = false)
    private String method;

    @Column(nullable = false)
    private String path;

    @Column(name = "query_string")
    private String queryString;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(nullable = false)
    private String resource;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false)
    private String summary;

    @Column(nullable = false)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_by_email")
    private String decidedByEmail;

    @Column(name = "decision_note")
    private String decisionNote;

    /** One-shot secret admitting the replay past the gate. Cleared once used. */
    @Column(name = "replay_nonce")
    private String replayNonce;

    @Column(name = "failure_detail")
    private String failureDetail;

    public boolean pending() {
        return "PENDING".equals(status);
    }
}
