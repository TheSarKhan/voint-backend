package com.starsoft.voint.question.dto;

import java.time.Instant;
import java.util.UUID;

import com.starsoft.voint.question.QuestionStatus;
import com.starsoft.voint.question.UnansweredQuestion;

/** Bir cavabsız sual — zəng detalı ekranı və "boşluqlar" siyahısı üçün. */
public record UnansweredQuestionResponse(
        UUID id,
        UUID tenantId,
        UUID callId,
        String question,
        String context,
        QuestionStatus status,
        UUID ragDocumentId,
        Instant createdAt,
        Instant resolvedAt
) {
    public static UnansweredQuestionResponse from(UnansweredQuestion q) {
        return new UnansweredQuestionResponse(q.getId(), q.getTenantId(), q.getCallId(),
                q.getQuestion(), q.getContext(), q.getStatus(), q.getRagDocumentId(),
                q.getCreatedAt(), q.getResolvedAt());
    }
}
