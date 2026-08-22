package com.starsoft.voint.outbound.dto;

public record OutboundCampaignUpdateRequest(
        String name,
        String campaignType,
        String status, // DRAFT, SCHEDULED, RUNNING, PAUSED, COMPLETED, CANCELLED
        String agentPrompt,
        String greetingText,
        String callingHoursStart,
        String callingHoursEnd,
        Integer maxRetries,
        Integer retryIntervalMinutes,
        Integer concurrencyLimit
) {
}
