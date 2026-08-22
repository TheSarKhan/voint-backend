package com.starsoft.voint.call.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.starsoft.voint.call.Call;
import com.starsoft.voint.call.CallStatus;
import com.starsoft.voint.crm.CallTranscript;
import com.starsoft.voint.question.dto.UnansweredQuestionResponse;

/**
 * Single-call view used by GET /tenants/{id}/calls/{callId}.
 * Includes transcript, AI summary, unanswered questions, and attached CRM customer card.
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
        String cleanedTranscript,
        String aiSummary,
        List<UnansweredQuestionResponse> unansweredQuestions,
        UUID customerId,
        String customerName,
        String customerNotes
) {
    public static CallDetailResponse from(Call c, CallTranscript transcript,
                                          List<UnansweredQuestionResponse> unansweredQuestions) {
        return from(c, transcript, unansweredQuestions, null, null, null);
    }

    public static CallDetailResponse from(Call c, CallTranscript transcript,
                                          List<UnansweredQuestionResponse> unansweredQuestions,
                                          UUID customerId, String customerName, String customerNotes) {
        return new CallDetailResponse(c.getId(), c.getTenantId(), c.getCallerNumber(), c.getLanguageDetected(),
                c.getStatus(), c.getDurationSeconds(), c.getStartedAt(), c.getEndedAt(),
                transcript != null ? transcript.getFullTranscript() : null,
                transcript != null ? transcript.getCleanedTranscript() : null,
                transcript != null ? transcript.getAiSummary() : null,
                unansweredQuestions,
                customerId, customerName, customerNotes);
    }
}
