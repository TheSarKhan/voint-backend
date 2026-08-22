package com.starsoft.voint.outbound.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;

public record OutboundContactAddRequest(
        @NotBlank String phoneNumber,
        String customerName,
        String customData
) {
    public record BulkAddRequest(
            List<OutboundContactAddRequest> contacts
    ) {}
}
