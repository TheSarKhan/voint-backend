package com.starsoft.voint.rbac.dto;

import java.util.List;
import java.util.UUID;

/** @param roleIds which of the template department's roles to copy too; null = all of them. */
public record DepartmentCopyRequest(List<UUID> roleIds) {
}
