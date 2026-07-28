package com.starsoft.voint.rbac.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A role plus its matrix, in the shape the editor renders directly.
 *
 * <p>{@code permissions} maps a resource name to the actions granted on it. Sending the grid this
 * way rather than as a flat list keeps the screen from having to group it, and keeps "no row" and
 * "empty list" meaning the same thing: not allowed.
 */
public record RoleDetail(
        UUID id,
        UUID tenantId,
        /** Null = filed under no department; the screen shows these last, under "Departamentsiz". */
        UUID departmentId,
        String departmentName,
        String name,
        String description,
        boolean template,
        /** Built-in: may be edited, must not be deleted. */
        boolean system,
        /** How many accounts currently use it - deleting one that is in use is refused. */
        long userCount,
        Map<String, List<String>> permissions
) {
}
