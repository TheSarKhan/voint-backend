package com.starsoft.voint.usage;

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
 * One conversation turn's AI consumption: what the LLM cost in tokens and how many characters
 * were handed to the TTS engine. Recorded per turn (not per call) because that is the unit we
 * actually generate - a call is many turns.
 *
 * <p>Call minutes are deliberately NOT stored here; they live on {@code calls.duration_seconds},
 * written once from Vapi's end-of-call-report.
 */
@Entity
@Table(name = "usage_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /** Vapi's call id when the webhook carried one - lets a turn be traced back to its call. */
    @Column(name = "vapi_call_id")
    private String vapiCallId;

    @Column(name = "occurred_at", nullable = false)
    @Builder.Default
    private Instant occurredAt = Instant.now();

    @Column(name = "prompt_tokens", nullable = false)
    @Builder.Default
    private int promptTokens = 0;

    @Column(name = "completion_tokens", nullable = false)
    @Builder.Default
    private int completionTokens = 0;

    /** Exactly the text streamed back to Vapi - i.e. exactly what the TTS provider bills for. */
    @Column(name = "tts_characters", nullable = false)
    @Builder.Default
    private int ttsCharacters = 0;
}
