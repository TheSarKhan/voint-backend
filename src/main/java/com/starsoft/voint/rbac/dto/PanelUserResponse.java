package com.starsoft.voint.rbac.dto;

import java.time.Instant;
import java.util.UUID;

import com.starsoft.voint.auth.PanelUser;

/** A panel account as the admin screen shows it. The password hash never leaves the server. */
public record PanelUserResponse(
        UUID id,
        UUID tenantId,
        String email,
        String fullName,
        UUID roleId,
        String roleName,
        String status,
        Instant lastLoginAt,
        Instant createdAt
) {
    public static PanelUserResponse from(PanelUser user, String roleName) {
        return new PanelUserResponse(user.getId(), user.getTenantId(), user.getEmail(),
                user.getFullName(), user.getRoleId(), roleName, user.getStatus(),
                user.getLastLoginAt(), user.getCreatedAt());
    }
}
