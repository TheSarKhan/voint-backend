package com.starsoft.voint.usage;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.starsoft.voint.auth.TenantAccessGuard;
import com.starsoft.voint.common.dto.PageResponse;
import com.starsoft.voint.tenant.dto.TenantResponse;
import com.starsoft.voint.usage.dto.BillingPlanUpdateRequest;
import com.starsoft.voint.usage.dto.UsageReport;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@Tag(name = "Usage & billing", description = "Per-tenant consumption, provider cost and invoicing")
public class UsageController {

    private final UsageService usageService;
    private final TenantAccessGuard tenantAccessGuard;

    @GetMapping("/api/v1/admin/usage")
    @Operation(summary = "Usage and billing per tenant, paginated and sorted (SUPER_ADMIN only)")
    public PageResponse<UsageReport> allTenants(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction) {
        tenantAccessGuard.requireSuperAdmin();
        return usageService.pagedReport(month, sort, direction, page, size);
    }

    @GetMapping("/api/v1/tenants/{tenantId}/usage")
    @Operation(summary = "Usage and billing for one tenant in a month")
    public UsageReport forTenant(@PathVariable UUID tenantId,
                                 @RequestParam(required = false) String month) {
        tenantAccessGuard.requireAccess(tenantId);
        return usageService.reportFor(tenantId, month);
    }

    @PutMapping("/api/v1/tenants/{tenantId}/billing")
    @Operation(summary = "Set a tenant's commercial terms (SUPER_ADMIN only)")
    public TenantResponse updateBillingPlan(@PathVariable UUID tenantId,
                                            @Valid @RequestBody BillingPlanUpdateRequest request) {
        // Deliberately stricter than requireAccess: a tenant admin must not be able to
        // rewrite their own price.
        tenantAccessGuard.requireSuperAdmin();
        return TenantResponse.from(usageService.updateBillingPlan(tenantId, request));
    }
}
