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

import com.starsoft.voint.rbac.dto.RoleDetail;
import com.starsoft.voint.rbac.dto.RoleUpsertRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@Tag(name = "Roles", description = "Roles and their permission matrix")
public class RoleController {

    private final RoleService roleService;

    /** The matrix's own vocabulary, so the screen never hardcodes a resource list that drifts. */
    @RequirePermission(resource = Permission.Resource.ROLE, action = Permission.Action.READ,
            tenantScoped = false)
    @GetMapping("/api/v1/admin/permissions/catalog")
    @Operation(summary = "Resources and actions the matrix can contain")
    public Map<String, Object> catalog() {
        return Map.of(
                "resources", List.of(Permission.Resource.values()).stream()
                        .map(r -> Map.of(
                                "value", r.name(),
                                "label", r.getLabel(),
                                "platformOnly", r.isPlatformOnly()))
                        .toList(),
                "actions", List.of(Permission.Action.values()).stream()
                        .map(a -> Map.of("value", a.name(), "label", a.getLabel()))
                        .toList());
    }

    /** Platform roles and templates when tenantId is absent; a business's own roles when given. */
    @RequirePermission(resource = Permission.Resource.ROLE, action = Permission.Action.READ,
            tenantScoped = false)
    @GetMapping("/api/v1/admin/roles")
    @Operation(summary = "Roles, with their permission matrix")
    public List<RoleDetail> list(@RequestParam(required = false) UUID tenantId) {
        return roleService.detailsFor(tenantId);
    }

    @RequirePermission(resource = Permission.Resource.ROLE, action = Permission.Action.CREATE,
            tenantScoped = false)
    @PostMapping("/api/v1/admin/roles")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a role")
    public RoleDetail create(@Valid @RequestBody RoleUpsertRequest request) {
        return roleService.create(request);
    }

    @RequirePermission(resource = Permission.Resource.ROLE, action = Permission.Action.UPDATE,
            tenantScoped = false)
    @PutMapping("/api/v1/admin/roles/{roleId}")
    @Operation(summary = "Rename a role and replace its permission matrix")
    public RoleDetail update(@PathVariable UUID roleId,
                             @Valid @RequestBody RoleUpsertRequest request) {
        return roleService.update(roleId, request);
    }

    @RequirePermission(resource = Permission.Resource.ROLE, action = Permission.Action.DELETE,
            tenantScoped = false)
    @DeleteMapping("/api/v1/admin/roles/{roleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a role; refused for built-in roles and roles still in use")
    public void delete(@PathVariable UUID roleId) {
        roleService.delete(roleId);
    }

    /** Copies a platform template into a business as an editable starting point. */
    @RequirePermission(resource = Permission.Resource.ROLE, action = Permission.Action.CREATE,
            tenantScoped = false)
    @PostMapping("/api/v1/admin/roles/{templateId}/copy-to/{tenantId}")
    @Operation(summary = "Copy a template role into a business, optionally into one of its departments")
    public RoleDetail copyTemplate(@PathVariable UUID templateId, @PathVariable UUID tenantId,
                                   @RequestParam(required = false) UUID departmentId) {
        return roleService.copyTemplateTo(templateId, tenantId, departmentId);
    }
}
