package com.starsoft.voint.outbound.dto;

import java.time.Instant;
import java.util.UUID;

import com.starsoft.voint.outbound.OutboundCampaign;

public record OutboundCampaignResponse(
        UUID id,
        UUID tenantId,
        String name,
        String campaignType,
        String status,
        String agentPrompt,
        String greetingText,
        String callingHoursStart,
        String callingHoursEnd,
        int maxRetries,
        int retryIntervalMinutes,
        int concurrencyLimit,
        int totalContacts,
        int contactedCount,
        int successfulCount,
        int failedCount,
        Instant createdAt,
        Instant updatedAt
) {
    public static OutboundCampaignResponse from(OutboundCampaign c) {
        return new OutboundCampaignResponse(
                c.getId(),
                c.getTenantId(),
                c.getName(),
                c.getCampaignType(),
                c.getStatus(),
                c.getAgentPrompt(),
                c.getGreetingText(),
                c.getCallingHoursStart(),
                c.getCallingHoursEnd(),
                c.getMaxRetries(),
                c.getRetryIntervalMinutes(),
                c.getConcurrencyLimit(),
                c.getTotalContacts(),
                c.getContactedCount(),
                c.getSuccessfulCount(),
                c.getFailedCount(),
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }
}
