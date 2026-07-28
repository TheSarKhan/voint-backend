package com.starsoft.voint.rbac.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;

/** @param tenantId null = a department of the platform itself */
public record DepartmentUpsertRequest(
        @NotBlank String name,
        String description,
        UUID tenantId
) {
}
