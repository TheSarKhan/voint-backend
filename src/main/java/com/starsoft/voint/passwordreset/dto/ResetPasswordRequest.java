package com.starsoft.voint.passwordreset.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** E-poçtdakı linkdən gələn token + istifadəçinin təyin etdiyi yeni şifrə. */
public record ResetPasswordRequest(
        @NotBlank String token,
        // Minimum uzunluq: qısa şifrə sıfırlama axınının bütün mənasını yox edir.
        @NotBlank @Size(min = 8, message = "Şifrə ən azı 8 simvol olmalıdır") String newPassword
) {
}
