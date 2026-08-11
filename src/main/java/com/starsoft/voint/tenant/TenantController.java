package com.starsoft.voint.tenant;

import com.starsoft.voint.rbac.Permission;
import com.starsoft.voint.rbac.PublicEndpoint;
import com.starsoft.voint.rbac.RequirePermission;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.RequestParam;

import com.starsoft.voint.auth.TenantAccessGuard;
import com.starsoft.voint.common.dto.PageRequests;
import com.starsoft.voint.common.dto.PageResponse;
import com.starsoft.voint.tenant.dto.TenantConfigUpdateRequest;
import com.starsoft.voint.tenant.dto.TenantCreateRequest;
import com.starsoft.voint.tenant.dto.TenantResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/tenants")
@RequiredArgsConstructor
@Tag(name = "Tenants", description = "Business (tenant) management")
public class TenantController {

    private final TenantService tenantService;
    private final TenantAccessGuard tenantAccessGuard;

    @RequirePermission(resource = Permission.Resource.TENANT, action = Permission.Action.CREATE, tenantScoped = false)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new tenant (business)")
    public TenantResponse create(@Valid @RequestBody TenantCreateRequest request) {
        return TenantResponse.from(tenantService.create(request));
    }

    @RequirePermission(resource = Permission.Resource.TENANT, action = Permission.Action.READ, tenantScoped = false)
    @GetMapping
    @Operation(summary = "Search tenants, paginated and sorted (SUPER_ADMIN only)")
    public PageResponse<TenantResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction) {
        tenantAccessGuard.requireSuperAdmin();
        Pageable pageable = PageRequests.of(page, size, sort, direction,
                TenantService.SORTABLE, TenantService.DEFAULT_SORT);
        return PageResponse.of(tenantService.search(q, pageable), TenantResponse::from);
    }

    // SETTINGS, not TENANT: TENANT is platform-only (see Permission.Resource), so no tenant role -
    // not even Sahib - could ever be granted it, and this endpoint is exactly how a tenant's own
    // panel (Ayarlar) reads its own profile. tenantScoped defaults to true here, same as
    // updateConfig() below; requireAccess() still runs in the body for the subdomain-lookup case,
    // where the path variable isn't a UUID the interceptor can compare itself.
    @RequirePermission(resource = Permission.Resource.SETTINGS, action = Permission.Action.READ)
    @GetMapping("/{key}")
    @Operation(summary = "Get a tenant by id or by subdomain")
    public TenantResponse get(@PathVariable String key) {
        // Resolve first, then guard on the real id: the caller may have addressed this tenant by
        // its subdomain, and permission is about which tenant it is, not how it was named.
        Tenant tenant = tenantService.getByIdOrSubdomain(key);
        tenantAccessGuard.requireAccess(tenant.getId());
        return TenantResponse.from(tenant);
    }

    @RequirePermission(resource = Permission.Resource.TENANT, action = Permission.Action.UPDATE, tenantScoped = false)
    @PostMapping("/{id}/vapi-sync")
    @Operation(summary = "Recreate or update this tenant's Vapi assistant (SUPER_ADMIN only)")
    public TenantResponse syncVapi(@PathVariable UUID id) {
        tenantAccessGuard.requireSuperAdmin();
        return TenantResponse.from(tenantService.syncAssistantOrThrow(id));
    }

    @RequirePermission(resource = Permission.Resource.TENANT, action = Permission.Action.UPDATE, tenantScoped = false)
    @PostMapping("/vapi-sync-all")
    @Operation(summary = "Re-push every tenant to Vapi - use after changing a platform-wide voice setting")
    public Map<String, Integer> syncAllVapi() {
        tenantAccessGuard.requireSuperAdmin();
        return Map.of("synced", tenantService.syncAllAssistants());
    }

    @RequirePermission(resource = Permission.Resource.SETTINGS, action = Permission.Action.UPDATE)
    @PutMapping("/{id}/config")
    @Operation(summary = "Update tenant configuration (greeting, working hours, handoff, languages)")
    public TenantResponse updateConfig(@PathVariable UUID id,
                                       @RequestBody TenantConfigUpdateRequest request) {
        tenantAccessGuard.requireAccess(id);
        return TenantResponse.from(tenantService.updateConfig(id, request));
    }
}
