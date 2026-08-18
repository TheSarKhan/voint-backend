package com.starsoft.voint.crm;

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

/** Full transcript + AI summary of one call (CRM call record). */
@Entity
@Table(name = "call_transcripts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallTranscript {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "call_id", nullable = false)
    private UUID callId;

    @Column(name = "full_transcript", columnDefinition = "text")
    private String fullTranscript;

    @Column(name = "ai_summary", columnDefinition = "text")
    private String aiSummary;

    /**
     * The same conversation, with interruption/mis-hearing noise (barge-in fragments, repeated
     * false starts) cleaned up for a business owner to actually read - never invents content
     * that wasn't said, see CallAnalysisService's prompt. {@code fullTranscript} (STT's raw
     * output) stays the source of truth; this is a readability layer on top of it, not a
     * replacement - the panel shows both.
     */
    @Column(name = "cleaned_transcript", columnDefinition = "text")
    private String cleanedTranscript;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    /**
     * Cavabsız sual təhlilinin işlədiyi an. NULL = hələ baxılmayıb (köhnə zənglər, və ya Gemini
     * əlçatmaz olduğu üçün buraxılanlar) — geriyə dönük doldurma məhz bunları götürür.
     * Sual tapılmayan zəngdə də dolur: "boşluq yoxdur" ilə "baxılmayıb" eyni şey deyil.
     */
    @Column(name = "analyzed_at")
    private Instant analyzedAt;
}
