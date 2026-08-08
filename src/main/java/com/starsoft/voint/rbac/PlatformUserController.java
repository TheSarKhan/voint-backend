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

/**
 * Login accounts for the admin panel itself - platform staff, not any one business.
 *
 * <p>Everything here calls {@link PanelUserService} with {@code tenantId = null}, which the
 * service already understood before this controller existed (it has handled a null tenant
 * throughout - email templates, role assignment, the "don't delete the last account" guard - it
 * simply had no caller that ever passed one).
 *
 * <p>Every method calls {@link TenantAccessGuard#requireSuperAdmin()} in addition to declaring
 * {@code USER} permission. That second check is not redundant: unlike {@code TENANT} or
 * {@code PROVIDER}, {@code USER} is deliberately grantable to a tenant's own role too (a business
 * needs to manage its own panel accounts), so a tenant role holding {@code USER:CREATE} for
 * legitimate reasons would otherwise also satisfy {@code @RequirePermission(tenantScoped = false)}
 * here and could create itself a platform account. {@code requireSuperAdmin()} closes that -
 * the same pattern {@link com.starsoft.voint.health.ProviderHealthController} already uses.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@Tag(name = "Platform users", description = "Login accounts for the admin panel (platform staff)")
public class PlatformUserController {

    private final PanelUserService userService;
    private final RoleService roleService;
    private final TenantAccessGuard tenantAccessGuard;

    @RequirePermission(resource = Permission.Resource.USER, action = Permission.Action.READ,
            tenantScoped = false)
    @GetMapping
    @Operation(summary = "Platform staff accounts")
    public List<PanelUserResponse> list() {
        tenantAccessGuard.requireSuperAdmin();
        return userService.listForTenant(null);
    }

    @RequirePermission(resource = Permission.Resource.USER, action = Permission.Action.READ,
            tenantScoped = false)
    @GetMapping("/assignable-roles")
    @Operation(summary = "Roles that can be given to a platform staff account")
    public List<Map<String, Object>> assignableRoles() {
        tenantAccessGuard.requireSuperAdmin();
        return roleService.assignableFor(null).stream()
                .map(r -> Map.<String, Object>of(
                        "id", r.getId(),
                        "name", r.getName(),
                        "description", r.getDescription() != null ? r.getDescription() : ""))
                .toList();
    }

    @RequirePermission(resource = Permission.Resource.USER, action = Permission.Action.CREATE,
            tenantScoped = false)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a platform staff account; the generated password is returned once")
    public PanelUserCreatedResponse create(@Valid @RequestBody PanelUserCreateRequest request) {
        tenantAccessGuard.requireSuperAdmin();
        return userService.create(null, request);
    }

    @RequirePermission(resource = Permission.Resource.USER, action = Permission.Action.UPDATE,
            tenantScoped = false)
    @PutMapping("/{userId}")
    @Operation(summary = "Edit the address and name; changing the address changes the login")
    public PanelUserResponse update(@PathVariable UUID userId,
                                    @Valid @RequestBody PanelUserUpdateRequest request) {
        tenantAccessGuard.requireSuperAdmin();
        return userService.update(null, userId, request);
    }

    @RequirePermission(resource = Permission.Resource.USER, action = Permission.Action.UPDATE,
            tenantScoped = false)
    @PostMapping("/{userId}/reset-password")
    @Operation(summary = "Issue a new password; the old one stops working immediately")
    public PanelUserCreatedResponse resetPassword(@PathVariable UUID userId) {
        tenantAccessGuard.requireSuperAdmin();
        return userService.resetPassword(null, userId);
    }

    @RequirePermission(resource = Permission.Resource.USER, action = Permission.Action.UPDATE,
            tenantScoped = false)
    @PutMapping("/{userId}/status")
    @Operation(summary = "Block or unblock an account")
    public PanelUserResponse setStatus(@PathVariable UUID userId,
                                        @RequestBody Map<String, String> body) {
        tenantAccessGuard.requireSuperAdmin();
        return userService.setStatus(null, userId, body.get("status"));
    }

    @RequirePermission(resource = Permission.Resource.USER, action = Permission.Action.UPDATE,
            tenantScoped = false)
    @PutMapping("/{userId}/role")
    @Operation(summary = "Change which role applies to this account")
    public PanelUserResponse changeRole(@PathVariable UUID userId,
                                         @RequestBody Map<String, UUID> body) {
        tenantAccessGuard.requireSuperAdmin();
        return userService.changeRole(null, userId, body.get("roleId"));
    }

    @RequirePermission(resource = Permission.Resource.USER, action = Permission.Action.DELETE,
            tenantScoped = false)
    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove an account; refused if it is the last active one")
    public void delete(@PathVariable UUID userId) {
        tenantAccessGuard.requireSuperAdmin();
        userService.delete(null, userId);
    }
}
