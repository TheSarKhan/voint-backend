package com.starsoft.voint.rbac.dto;

import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PanelUserCreateRequest(
        @NotBlank @Email String email,
        String fullName,
        @NotNull UUID roleId
) {
}
