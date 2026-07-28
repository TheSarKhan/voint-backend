package com.starsoft.voint.rbac.dto;

import java.util.UUID;

public record DepartmentDetail(
        UUID id,
        UUID tenantId,
        String name,
        String description,
        long roleCount
) {
}
