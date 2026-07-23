package com.starsoft.voint.call.dto;

import java.time.Instant;
import java.util.UUID;

import com.starsoft.voint.call.Call;
import com.starsoft.voint.call.CallStatus;
import com.starsoft.voint.crm.CallTranscript;

/**
 * Single-call view used by GET /tenants/{id}/calls/{callId}.
 * Includes the transcript + AI summary when one has been recorded for this call
 * (the current webhook flow does not write transcripts yet, so both fields are
 * nullable rather than a 404/error).
 */
public record CallDetailResponse(
        UUID id,
        UUID tenantId,
        String callerNumber,
        String languageDetected,
        CallStatus status,
        Integer durationSeconds,
        Instant startedAt,
        Instant endedAt,
        String fullTranscript,
        String aiSummary
) {
    public static CallDetailResponse from(Call c, CallTranscript transcript) {
        return new CallDetailResponse(c.getId(), c.getTenantId(), c.getCallerNumber(), c.getLanguageDetected(),
                c.getStatus(), c.getDurationSeconds(), c.getStartedAt(), c.getEndedAt(),
                transcript != null ? transcript.getFullTranscript() : null,
                transcript != null ? transcript.getAiSummary() : null);
    }
}
