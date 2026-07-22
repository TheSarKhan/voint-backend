package com.starsoft.voint.tenant.dto;

/** Partial config update - null fields are left unchanged. */
public record TenantConfigUpdateRequest(
        String phoneNumber,
        String greetingText,
        String workingHours,
        String handoffNumber,
        String languageConfig
) {
}
