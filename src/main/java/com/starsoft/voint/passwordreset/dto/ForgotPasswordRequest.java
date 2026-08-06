package com.starsoft.voint.passwordreset.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** "Şifrəmi unutdum" — yalnız e-poçt. */
public record ForgotPasswordRequest(
        @NotBlank @Email String email
) {
}
