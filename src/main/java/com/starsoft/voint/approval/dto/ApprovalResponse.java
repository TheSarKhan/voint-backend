package com.starsoft.voint.approval.dto;

import java.time.Instant;
import java.util.UUID;

import com.starsoft.voint.approval.ApprovalRequest;

/**
 * One entry of the queue, as the screen shows it.
 *
 * <p>{@code method}, {@code path} and {@code body} are included even though {@code summary} reads
 * better: the summary can only say "Müştərilər · Sil", and a person being asked to authorise a
 * deletion is entitled to see exactly what was requested rather than a category.
 */
public record ApprovalResponse(
        UUID id,
        String requestedByEmail,
        String method,
        String path,
        String body,
        String resource,
        String action,
        String summary,
        String status,
        Instant createdAt,
        Instant decidedAt,
        String decidedByEmail,
        String decisionNote,
        String failureDetail
) {
    public static ApprovalResponse from(ApprovalRequest r) {
        return new ApprovalResponse(r.getId(), r.getRequestedByEmail(), r.getMethod(), r.getPath(),
                r.getBody(), r.getResource(), r.getAction(), r.getSummary(), r.getStatus(),
                r.getCreatedAt(), r.getDecidedAt(), r.getDecidedByEmail(), r.getDecisionNote(),
                r.getFailureDetail());
    }
}
