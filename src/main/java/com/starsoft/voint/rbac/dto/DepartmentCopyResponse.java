package com.starsoft.voint.rbac.dto;

import java.util.List;

/** A template department copied into a tenant, plus every role that came with it. */
public record DepartmentCopyResponse(
        DepartmentDetail department,
        List<RoleDetail> copiedRoles
) {
}
