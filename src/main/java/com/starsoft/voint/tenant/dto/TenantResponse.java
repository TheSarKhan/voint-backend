package com.starsoft.voint.tenant.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.starsoft.voint.tenant.Tenant;

public record TenantResponse(
        UUID id,
        String name,
        String phoneNumber,
        String greetingText,
        String workingHours,
        String handoffNumber,
        String languageConfig,
        BigDecimal monthlyFee,
        Integer includedMinutes,
        BigDecimal overagePerMinute,
        Integer monthlyMinuteCap,
        String vapiAssistantId,
        String sttDomain,
        String sttTopic,
        String sttVocabulary,
        Instant createdAt
) {
    public static TenantResponse from(Tenant t) {
        return new TenantResponse(t.getId(), t.getName(), t.getPhoneNumber(), t.getGreetingText(),
                t.getWorkingHours(), t.getHandoffNumber(), t.getLanguageConfig(),
                t.getMonthlyFee(), t.getIncludedMinutes(), t.getOveragePerMinute(),
                t.getMonthlyMinuteCap(), t.getVapiAssistantId(),
                t.getSttDomain(), t.getSttTopic(), t.getSttVocabulary(), t.getCreatedAt());
    }
}
