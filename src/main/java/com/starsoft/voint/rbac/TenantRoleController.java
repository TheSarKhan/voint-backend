package com.starsoft.voint.rbac;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

import com.starsoft.voint.auth.TenantAccessGuard;
import com.starsoft.voint.rbac.dto.RoleDetail;
import com.starsoft.voint.rbac.dto.RoleUpsertRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * A business managing its own roles - the tenant-side counterpart to {@link RoleController}, which
 * remains platform-only. Every write here is forced onto the {@code tenantId} path variable
 * regardless of what a request body claims, and every read/write of an existing role first checks
 * {@link RoleService#requireOwnedByTenant} - a tenant may never see or touch another business's
 * (or the platform's) roles through this route.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/tenants/{tenantId}/roles")
@Tag(name = "Tenant roles", description = "A business's own roles and their permission matrix")
public class TenantRoleController {

    private final RoleService roleService;
    private final TenantAccessGuard tenantAccessGuard;

    @RequirePermission(resource = Permission.Resource.ROLE, action = Permission.Action.READ)
    @GetMapping("/permissions/catalog")
    @Operation(summary = "Resources and actions a tenant role may hold")
    public Map<String, Object> catalog(@PathVariable UUID tenantId) {
        tenantAccessGuard.requireAccess(tenantId);
        return Map.of(
                "resources", Permission.tenantResources().stream()
                        .map(r -> Map.of("value", r.name(), "label", r.getLabel()))
                        .toList(),
                "actions", List.of(Permission.Action.values()).stream()
                        .map(a -> Map.of("value", a.name(), "label", a.getLabel()))
                        .toList());
    }

    @RequirePermission(resource = Permission.Resource.ROLE, action = Permission.Action.READ)
    @GetMapping
    @Operation(summary = "This business's own roles")
    public List<RoleDetail> list(@PathVariable UUID tenantId) {
        tenantAccessGuard.requireAccess(tenantId);
        return roleService.detailsFor(tenantId);
    }

    /** Platform templates this business may copy from - never its own roles, never another tenant's. */
    @RequirePermission(resource = Permission.Resource.ROLE, action = Permission.Action.READ)
    @GetMapping("/templates")
    @Operation(summary = "Platform role templates available to copy")
    public List<RoleDetail> templates(@PathVariable UUID tenantId) {
        tenantAccessGuard.requireAccess(tenantId);
        return roleService.templates();
    }

    @RequirePermission(resource = Permission.Resource.ROLE, action = Permission.Action.CREATE)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a role for this business")
    public RoleDetail create(@PathVariable UUID tenantId, @Valid @RequestBody RoleUpsertRequest request) {
        tenantAccessGuard.requireAccess(tenantId);
        // tenantId and template are forced from the path/route, never taken from the body - a
        // tenant user must not be able to create a platform template or another business's role.
        RoleUpsertRequest scoped = new RoleUpsertRequest(request.name(), request.description(),
                tenantId, request.departmentId(), false, request.permissions());
        return roleService.create(scoped);
    }

    @RequirePermission(resource = Permission.Resource.ROLE, action = Permission.Action.UPDATE)
    @PutMapping("/{roleId}")
    @Operation(summary = "Rename this business's role and replace its permission matrix")
    public RoleDetail update(@PathVariable UUID tenantId, @PathVariable UUID roleId,
                             @Valid @RequestBody RoleUpsertRequest request) {
        tenantAccessGuard.requireAccess(tenantId);
        roleService.requireOwnedByTenant(roleId, tenantId);
        return roleService.update(roleId, request);
    }

    @RequirePermission(resource = Permission.Resource.ROLE, action = Permission.Action.DELETE)
    @DeleteMapping("/{roleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete this business's role; refused for its owner role and roles still in use")
    public void delete(@PathVariable UUID tenantId, @PathVariable UUID roleId) {
        tenantAccessGuard.requireAccess(tenantId);
        roleService.requireOwnedByTenant(roleId, tenantId);
        roleService.delete(roleId);
    }

    /** Copies a platform template into this business as an editable starting point. */
    @RequirePermission(resource = Permission.Resource.ROLE, action = Permission.Action.CREATE)
    @PostMapping("/{templateId}/copy-from-template")
    @Operation(summary = "Copy a template role into this business, optionally into one of its departments")
    public RoleDetail copyFromTemplate(@PathVariable UUID tenantId, @PathVariable UUID templateId,
                                       @RequestParam(required = false) UUID departmentId) {
        tenantAccessGuard.requireAccess(tenantId);
        return roleService.copyTemplateTo(templateId, tenantId, departmentId);
    }
}
