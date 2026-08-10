package com.starsoft.voint.auth.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.starsoft.voint.auth.PanelUser;

public record MeResponse(
        UUID id,
        UUID tenantId,
        String email,
        /** Legacy - SUPER_ADMIN/ADMIN, doğru icazələr roleId arxasındadır. */
        String role,
        /** Granular rolun görünən adı (məs. "Platforma admini", "Operator") - ekranda göstərmək üçün. */
        String roleName,
        /** resurs adı -> icazə verilmiş əməliyyatlar. Nav/düymələr bunu göstərməzdən əvvəl yoxlasın. */
        Map<String, List<String>> permissions
) {
    public static MeResponse from(PanelUser u, String roleName, Map<String, List<String>> permissions) {
        return new MeResponse(u.getId(), u.getTenantId(), u.getEmail(), u.getRole(), roleName, permissions);
    }
}
