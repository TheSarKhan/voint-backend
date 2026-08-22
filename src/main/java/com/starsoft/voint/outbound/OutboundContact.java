package com.starsoft.voint.outbound;

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

@Entity
@Table(name = "outbound_contacts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboundContact {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "phone_number", nullable = false)
    private String phoneNumber;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "custom_data", columnDefinition = "TEXT")
    private String customData;

    /** PENDING, QUEUED, DIALING, ANSWERED, BUSY, NO_ANSWER, FAILED, DO_NOT_CALL */
    @Column(nullable = false)
    @Builder.Default
    private String status = "PENDING";

    /** INTERESTED, CALLBACK_REQUESTED, NOT_INTERESTED, CONVERTED, UNREACHABLE */
    @Column(name = "call_outcome")
    private String callOutcome;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "call_id")
    private UUID callId;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
