package com.starsoft.voint.tenant;

import java.math.BigDecimal;
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

/** A tenant = one business using the Voint platform (e.g. CES equipment rental). */
@Entity
@Table(name = "tenants")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    /** Phone number Vapi routes to this tenant. */
    @Column(name = "phone_number")
    private String phoneNumber;

    /** First sentence the agent says when answering a call. */
    @Column(name = "greeting_text")
    private String greetingText;

    /** Free-form working hours description, e.g. "B.e-Cümə 09:00-18:00". */
    @Column(name = "working_hours")
    private String workingHours;

    /** Human operator number for handoff. */
    @Column(name = "handoff_number")
    private String handoffNumber;

    /** Language settings (JSON string for now, e.g. {"default":"az","supported":["az","ru","en"]}). */
    @Column(name = "language_config")
    private String languageConfig;

    /** Fixed monthly charge in AZN, billed whether or not the tenant makes any calls. */
    @Column(name = "monthly_fee", nullable = false)
    @Builder.Default
    private BigDecimal monthlyFee = BigDecimal.ZERO;

    /** Call minutes covered by {@link #monthlyFee} before overage applies. */
    @Column(name = "included_minutes", nullable = false)
    @Builder.Default
    private int includedMinutes = 0;

    /** AZN charged per minute beyond {@link #includedMinutes}. */
    @Column(name = "overage_per_minute", nullable = false)
    @Builder.Default
    private BigDecimal overagePerMinute = BigDecimal.ZERO;

    /**
     * Hard monthly ceiling in minutes; 0 means none. Distinct from {@link #includedMinutes}:
     * that one decides what a call costs the customer, this one decides when we stop answering
     * at all, so a runaway on their side cannot quietly drain a month of credits.
     */
    @Column(name = "monthly_minute_cap", nullable = false)
    @Builder.Default
    private int monthlyMinuteCap = 0;

    /**
     * Panel address label: "ces" serves this tenant at ces.voint.az. Unique across the platform,
     * lowercase - see {@link Subdomains} for what is allowed and what is reserved.
     */
    @Column(name = "subdomain", unique = true)
    private String subdomain;

    /** Vapi's id for this tenant's assistant; null until it has been provisioned. */
    @Column(name = "vapi_assistant_id")
    private String vapiAssistantId;

    /** What this business does, given to the transcriber as context, e.g. "Diş klinikası". */
    @Column(name = "stt_domain")
    private String sttDomain;

    /** What callers typically ask about - helps the transcriber pick the right homophone. */
    @Column(name = "stt_topic")
    private String sttTopic;

    /**
     * Comma-separated industry terms the transcriber should expect. Per tenant on purpose: an
     * equipment vocabulary would actively damage a clinic's recognition. Azerbaijani place names
     * are added platform-wide and must not be repeated here.
     */
    @Column(name = "stt_vocabulary")
    private String sttVocabulary;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
