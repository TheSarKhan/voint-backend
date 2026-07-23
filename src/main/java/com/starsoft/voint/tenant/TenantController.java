package com.starsoft.voint.tenant;

import java.util.List;
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

import com.starsoft.voint.auth.TenantAccessGuard;
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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new tenant (business)")
    public TenantResponse create(@Valid @RequestBody TenantCreateRequest request) {
        return TenantResponse.from(tenantService.create(request));
    }

    @GetMapping
    @Operation(summary = "List all tenants (SUPER_ADMIN only)")
    public List<TenantResponse> list() {
        tenantAccessGuard.requireSuperAdmin();
        return tenantService.list().stream().map(TenantResponse::from).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get tenant by id")
    public TenantResponse get(@PathVariable UUID id) {
        tenantAccessGuard.requireAccess(id);
        return TenantResponse.from(tenantService.get(id));
    }

    @PutMapping("/{id}/config")
    @Operation(summary = "Update tenant configuration (greeting, working hours, handoff, languages)")
    public TenantResponse updateConfig(@PathVariable UUID id,
                                       @RequestBody TenantConfigUpdateRequest request) {
        tenantAccessGuard.requireAccess(id);
        return TenantResponse.from(tenantService.updateConfig(id, request));
    }
}
