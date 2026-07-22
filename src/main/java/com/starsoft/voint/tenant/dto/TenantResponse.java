package com.starsoft.voint.tenant.dto;

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
        Instant createdAt
) {
    public static TenantResponse from(Tenant t) {
        return new TenantResponse(t.getId(), t.getName(), t.getPhoneNumber(), t.getGreetingText(),
                t.getWorkingHours(), t.getHandoffNumber(), t.getLanguageConfig(), t.getCreatedAt());
    }
}
