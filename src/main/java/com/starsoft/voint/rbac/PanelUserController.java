package com.starsoft.voint.rbac;

import com.starsoft.voint.rbac.Permission;
import com.starsoft.voint.rbac.PublicEndpoint;
import com.starsoft.voint.rbac.RequirePermission;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.starsoft.voint.auth.TenantAccessGuard;
import com.starsoft.voint.rbac.dto.PanelUserCreateRequest;
import com.starsoft.voint.rbac.dto.PanelUserCreatedResponse;
import com.starsoft.voint.rbac.dto.PanelUserResponse;
import com.starsoft.voint.rbac.dto.PanelUserUpdateRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/users")
@RequiredArgsConstructor
@Tag(name = "Panel users", description = "Login accounts for a business")
public class PanelUserController {

    private final PanelUserService userService;
    private final RoleService roleService;
    private final TenantAccessGuard tenantAccessGuard;

    @RequirePermission(resource = Permission.Resource.USER, action = Permission.Action.READ)
    @GetMapping
    @Operation(summary = "Accounts belonging to this business")
    public List<PanelUserResponse> list(@PathVariable UUID tenantId) {
        tenantAccessGuard.requireAccess(tenantId);
        return userService.listForTenant(tenantId);
    }

    /** Roles this business may assign - its own, or the platform templates. */
    @RequirePermission(resource = Permission.Resource.USER, action = Permission.Action.READ)
    @GetMapping("/assignable-roles")
    @Operation(summary = "Roles that can be given to a user here")
    public List<Map<String, Object>> assignableRoles(@PathVariable UUID tenantId) {
        tenantAccessGuard.requireAccess(tenantId);
        return roleService.assignableFor(tenantId).stream()
                .map(r -> Map.<String, Object>of(
                        "id", r.getId(),
                        "name", r.getName(),
                        "description", r.getDescription() != null ? r.getDescription() : ""))
                .toList();
    }

    @RequirePermission(resource = Permission.Resource.USER, action = Permission.Action.CREATE)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an account; the generated password is returned once")
    public PanelUserCreatedResponse create(@PathVariable UUID tenantId,
                                           @Valid @RequestBody PanelUserCreateRequest request) {
        tenantAccessGuard.requireAccess(tenantId);
        return userService.create(tenantId, request);
    }

    @RequirePermission(resource = Permission.Resource.USER, action = Permission.Action.UPDATE)
    @PutMapping("/{userId}")
    @Operation(summary = "Edit the address and name; changing the address changes the login")
    public PanelUserResponse update(@PathVariable UUID tenantId,
                                    @PathVariable UUID userId,
                                    @Valid @RequestBody PanelUserUpdateRequest request) {
        tenantAccessGuard.requireAccess(tenantId);
        return userService.update(tenantId, userId, request);
    }

    @RequirePermission(resource = Permission.Resource.USER, action = Permission.Action.UPDATE)
    @PostMapping("/{userId}/reset-password")
    @Operation(summary = "Issue a new password; the old one stops working immediately")
    public PanelUserCreatedResponse resetPassword(@PathVariable UUID tenantId,
                                                   @PathVariable UUID userId) {
        tenantAccessGuard.requireAccess(tenantId);
        return userService.resetPassword(tenantId, userId);
    }

    @RequirePermission(resource = Permission.Resource.USER, action = Permission.Action.UPDATE)
    @PutMapping("/{userId}/status")
    @Operation(summary = "Block or unblock an account")
    public PanelUserResponse setStatus(@PathVariable UUID tenantId,
                                        @PathVariable UUID userId,
                                        @RequestBody Map<String, String> body) {
        tenantAccessGuard.requireAccess(tenantId);
        return userService.setStatus(tenantId, userId, body.get("status"));
    }

    @RequirePermission(resource = Permission.Resource.USER, action = Permission.Action.UPDATE)
    @PutMapping("/{userId}/role")
    @Operation(summary = "Change which role applies to this account")
    public PanelUserResponse changeRole(@PathVariable UUID tenantId,
                                         @PathVariable UUID userId,
                                         @RequestBody Map<String, UUID> body) {
        tenantAccessGuard.requireAccess(tenantId);
        return userService.changeRole(tenantId, userId, body.get("roleId"));
    }

    @RequirePermission(resource = Permission.Resource.USER, action = Permission.Action.DELETE)
    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove an account; refused if it is the last active one")
    public void delete(@PathVariable UUID tenantId, @PathVariable UUID userId) {
        tenantAccessGuard.requireAccess(tenantId);
        userService.delete(tenantId, userId);
    }
}
