package com.starsoft.voint.outbound.dto;

import java.time.Instant;
import java.util.UUID;

import com.starsoft.voint.outbound.OutboundContact;

public record OutboundContactResponse(
        UUID id,
        UUID campaignId,
        UUID tenantId,
        String phoneNumber,
        String customerName,
        String customData,
        String status,
        String callOutcome,
        int retryCount,
        Instant nextAttemptAt,
        Instant lastAttemptAt,
        UUID callId,
        String summary,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {
    public static OutboundContactResponse from(OutboundContact c) {
        return new OutboundContactResponse(
                c.getId(),
                c.getCampaignId(),
                c.getTenantId(),
                c.getPhoneNumber(),
                c.getCustomerName(),
                c.getCustomData(),
                c.getStatus(),
                c.getCallOutcome(),
                c.getRetryCount(),
                c.getNextAttemptAt(),
                c.getLastAttemptAt(),
                c.getCallId(),
                c.getSummary(),
                c.getNotes(),
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }
}
