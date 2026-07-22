package com.starsoft.voint.auth.dto;

import java.util.UUID;

import com.starsoft.voint.auth.PanelUser;

public record MeResponse(
        UUID id,
        UUID tenantId,
        String email,
        String role
) {
    public static MeResponse from(PanelUser u) {
        return new MeResponse(u.getId(), u.getTenantId(), u.getEmail(), u.getRole());
    }
}
