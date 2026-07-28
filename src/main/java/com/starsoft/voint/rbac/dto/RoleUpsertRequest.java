package com.starsoft.voint.rbac.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;

/**
 * @param tenantId    null = a platform role or template
 * @param permissions resource name -> granted action names. Replaces the matrix wholesale rather
 *                    than patching it: the editor always sends the full grid it is showing, so a
 *                    partial update could silently keep a permission the operator just unticked.
 */
public record RoleUpsertRequest(
        @NotBlank String name,
        String description,
        UUID tenantId,
        boolean template,
        Map<String, List<String>> permissions
) {
}
