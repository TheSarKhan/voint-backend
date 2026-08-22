package com.starsoft.voint.outbound.dto;

import jakarta.validation.constraints.NotBlank;

public record OutboundCampaignCreateRequest(
        @NotBlank String name,
        String campaignType, // SALES_OUTBOUND, APPOINTMENT_REMINDER, PAYMENT_REMINDER, FEEDBACK_SURVEY, WINBACK
        String agentPrompt,
        String greetingText,
        String callingHoursStart,
        String callingHoursEnd,
        Integer maxRetries,
        Integer retryIntervalMinutes,
        Integer concurrencyLimit
) {
}
