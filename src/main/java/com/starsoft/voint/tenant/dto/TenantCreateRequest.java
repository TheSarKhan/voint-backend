package com.starsoft.voint.tenant.dto;

import jakarta.validation.constraints.NotBlank;

public record TenantCreateRequest(
        @NotBlank String name,
        String phoneNumber,
        String greetingText,
        String workingHours,
        String handoffNumber,
        String languageConfig
) {
}
