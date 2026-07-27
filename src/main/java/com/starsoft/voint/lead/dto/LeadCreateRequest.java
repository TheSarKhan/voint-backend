package com.starsoft.voint.lead.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The public landing form. Every field is unauthenticated input, so the sizes here are not
 * cosmetic - they are what stops a form post from writing arbitrarily large rows.
 */
public record LeadCreateRequest(

        @NotBlank(message = "Ad Soyad boş ola bilməz")
        @Size(max = 160)
        String fullName,

        @NotBlank(message = "Şirkət adı boş ola bilməz")
        @Size(max = 160)
        String company,

        @Size(max = 80)
        String industry,

        @NotBlank(message = "Telefon boş ola bilməz")
        @Size(max = 40)
        String phone,

        @NotBlank(message = "Email boş ola bilməz")
        @Email(message = "Email düzgün deyil")
        @Size(max = 160)
        String email,

        @Size(max = 40)
        String dailyCallVolume) {
}
