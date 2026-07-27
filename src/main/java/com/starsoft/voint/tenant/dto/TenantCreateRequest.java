package com.starsoft.voint.tenant.dto;

import jakarta.validation.constraints.NotBlank;

public record TenantCreateRequest(
        @NotBlank String name,
        String phoneNumber,
        String greetingText,
        String workingHours,
        String handoffNumber,
        String languageConfig,
        /** What this business does - given to the transcriber, e.g. "Dis klinikasi". */
        String sttDomain,
        /** What callers usually ask about, so the transcriber picks the right homophone. */
        String sttTopic,
        /** Comma-separated industry terms. Place names are added platform-wide, not here. */
        String sttVocabulary
) {
}
