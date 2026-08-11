package com.starsoft.voint.rbac;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.starsoft.voint.auth.TenantAccessGuard;
import com.starsoft.voint.rbac.dto.DepartmentCopyRequest;
import com.starsoft.voint.rbac.dto.DepartmentCopyResponse;
import com.starsoft.voint.rbac.dto.DepartmentDetail;
import com.starsoft.voint.rbac.dto.DepartmentUpsertRequest;
import com.starsoft.voint.rbac.dto.RoleDetail;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * A business managing its own departments - the tenant-side counterpart to
 * {@link DepartmentController}, which remains platform-only. Same rule as
 * {@link TenantRoleController}: writes are forced onto the {@code tenantId} path variable, and
 * every existing department is ownership-checked before being read or touched.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/tenants/{tenantId}/departments")
@Tag(name = "Tenant departments", description = "A business's own departments")
public class TenantDepartmentController {

    private final DepartmentService departmentService;
    private final RoleService roleService;
    private final TenantAccessGuard tenantAccessGuard;

    @RequirePermission(resource = Permission.Resource.ROLE, action = Permission.Action.READ)
    @GetMapping
    @Operation(summary = "This business's own departments")
    public List<DepartmentDetail> list(@PathVariable UUID tenantId) {
        tenantAccessGuard.requireAccess(tenantId);
        return departmentService.list(tenantId);
    }

    /** Platform departments this business may copy from, with their roles offered along. */
    @RequirePermission(resource = Permission.Resource.ROLE, action = Permission.Action.READ)
    @GetMapping("/templates")
    @Operation(summary = "Platform department templates available to copy")
    public List<DepartmentDetail> templates(@PathVariable UUID tenantId) {
        tenantAccessGuard.requireAccess(tenantId);
        return departmentService.list(null);
    }

    /** A department's own roles - the picklist before copying it, or just browsing what's inside. */
    @RequirePermission(resource = Permission.Resource.ROLE, action = Permission.Action.READ)
    @GetMapping("/{id}/roles")
    @Operation(summary = "Roles filed under one department")
    public List<RoleDetail> roles(@PathVariable UUID tenantId, @PathVariable UUID id) {
        tenantAccessGuard.requireAccess(tenantId);
        departmentService.requireVisibleToTenant(id, tenantId);
        return roleService.detailsForDepartment(id);
    }

    @RequirePermission(resource = Permission.Resource.ROLE, action = Permission.Action.CREATE)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a department for this business")
    public DepartmentDetail create(@PathVariable UUID tenantId, @Valid @RequestBody DepartmentUpsertRequest request) {
        tenantAccessGuard.requireAccess(tenantId);
        DepartmentUpsertRequest scoped = new DepartmentUpsertRequest(request.name(), request.description(), tenantId);
        return departmentService.create(scoped);
    }

    @RequirePermission(resource = Permission.Resource.ROLE, action = Permission.Action.UPDATE)
    @PutMapping("/{id}")
    @Operation(summary = "Rename this business's department")
    public DepartmentDetail update(@PathVariable UUID tenantId, @PathVariable UUID id,
                                   @Valid @RequestBody DepartmentUpsertRequest request) {
        tenantAccessGuard.requireAccess(tenantId);
        departmentService.requireOwnedByTenant(id, tenantId);
        return departmentService.update(id, request);
    }

    /** Copies a template department - and (by default) every role filed under it - into this business. */
    @RequirePermission(resource = Permission.Resource.ROLE, action = Permission.Action.CREATE)
    @PostMapping("/{id}/copy-from-template")
    @Operation(summary = "Copy a template department, with its roles, into this business")
    public DepartmentCopyResponse copyFromTemplate(@PathVariable UUID tenantId, @PathVariable UUID id,
                                                    @RequestBody(required = false) DepartmentCopyRequest request) {
        tenantAccessGuard.requireAccess(tenantId);
        return roleService.copyDepartmentTo(id, tenantId, request != null ? request.roleIds() : null);
    }

    @RequirePermission(resource = Permission.Resource.ROLE, action = Permission.Action.DELETE)
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete this business's department; its roles are kept and become ungrouped")
    public void delete(@PathVariable UUID tenantId, @PathVariable UUID id) {
        tenantAccessGuard.requireAccess(tenantId);
        departmentService.requireOwnedByTenant(id, tenantId);
        departmentService.delete(id);
    }
}
