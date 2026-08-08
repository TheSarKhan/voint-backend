package com.starsoft.voint.auth.dto;

import java.util.UUID;

import com.starsoft.voint.auth.PanelUser;

public record MeResponse(
        UUID id,
        UUID tenantId,
        String email,
        /** Legacy - SUPER_ADMIN/ADMIN, doğru icazələr roleId arxasındadır. */
        String role,
        /** Granular rolun görünən adı (məs. "Platforma admini", "Operator") - ekranda göstərmək üçün. */
        String roleName
) {
    public static MeResponse from(PanelUser u, String roleName) {
        return new MeResponse(u.getId(), u.getTenantId(), u.getEmail(), u.getRole(), roleName);
    }
}
