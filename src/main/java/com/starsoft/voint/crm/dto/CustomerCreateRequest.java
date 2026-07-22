package com.starsoft.voint.crm.dto;

import jakarta.validation.constraints.NotBlank;

public record CustomerCreateRequest(
        @NotBlank String phoneNumber,
        String name,
        String notes
) {
}
