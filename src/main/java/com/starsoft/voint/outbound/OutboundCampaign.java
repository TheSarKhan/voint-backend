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
@Table(name = "outbound_campaigns")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboundCampaign {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String name;

    /** SALES_OUTBOUND, APPOINTMENT_REMINDER, PAYMENT_REMINDER, FEEDBACK_SURVEY, WINBACK */
    @Column(name = "campaign_type", nullable = false)
    @Builder.Default
    private String campaignType = "SALES_OUTBOUND";

    /** DRAFT, SCHEDULED, RUNNING, PAUSED, COMPLETED, CANCELLED */
    @Column(nullable = false)
    @Builder.Default
    private String status = "DRAFT";

    @Column(name = "agent_prompt", columnDefinition = "TEXT")
    private String agentPrompt;

    @Column(name = "greeting_text", columnDefinition = "TEXT")
    private String greetingText;

    @Column(name = "calling_hours_start", nullable = false)
    @Builder.Default
    private String callingHoursStart = "10:00";

    @Column(name = "calling_hours_end", nullable = false)
    @Builder.Default
    private String callingHoursEnd = "19:00";

    @Column(name = "max_retries", nullable = false)
    @Builder.Default
    private int maxRetries = 2;

    @Column(name = "retry_interval_minutes", nullable = false)
    @Builder.Default
    private int retryIntervalMinutes = 60;

    @Column(name = "concurrency_limit", nullable = false)
    @Builder.Default
    private int concurrencyLimit = 1;

    @Column(name = "total_contacts", nullable = false)
    @Builder.Default
    private int totalContacts = 0;

    @Column(name = "contacted_count", nullable = false)
    @Builder.Default
    private int contactedCount = 0;

    @Column(name = "successful_count", nullable = false)
    @Builder.Default
    private int successfulCount = 0;

    @Column(name = "failed_count", nullable = false)
    @Builder.Default
    private int failedCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
