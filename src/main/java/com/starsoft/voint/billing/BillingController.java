package com.starsoft.voint.billing;

import java.util.*;
import org.springframework.web.bind.annotation.*;
import com.starsoft.voint.auth.TenantAccessGuard;
import com.starsoft.voint.billing.dto.*;
import com.starsoft.voint.rbac.*;
import com.starsoft.voint.tenant.dto.TenantResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Platform-only commercial administration. Tenant staff never get to alter their own price. */
@RestController @RequiredArgsConstructor
@RequestMapping("/api/v1/admin/billing")
public class BillingController {
    private final BillingService billing;
    private final TenantAccessGuard access;

    @GetMapping("/plans") @RequirePermission(resource = Permission.Resource.BILLING, action = Permission.Action.READ, tenantScoped = false)
    public List<BillingPlanResponse> plans() { access.requireSuperAdmin(); return billing.listPlans(); }

    @PostMapping("/plans") @RequirePermission(resource = Permission.Resource.BILLING, action = Permission.Action.CREATE, tenantScoped = false)
    public BillingPlanResponse createPlan(@Valid @RequestBody BillingPlanRequest request) { access.requireSuperAdmin(); return billing.createPlan(request); }

    @PutMapping("/plans/{id}") @RequirePermission(resource = Permission.Resource.BILLING, action = Permission.Action.UPDATE, tenantScoped = false)
    public BillingPlanResponse updatePlan(@PathVariable UUID id, @Valid @RequestBody BillingPlanRequest request) { access.requireSuperAdmin(); return billing.updatePlan(id, request); }

    @PutMapping("/tenants/{tenantId}/profile") @RequirePermission(resource = Permission.Resource.BILLING, action = Permission.Action.UPDATE, tenantScoped = false)
    public TenantResponse profile(@PathVariable UUID tenantId, @Valid @RequestBody BillingProfileRequest request) { access.requireSuperAdmin(); return TenantResponse.from(billing.updateProfile(tenantId, request)); }

    @GetMapping("/invoices") @RequirePermission(resource = Permission.Resource.BILLING, action = Permission.Action.READ, tenantScoped = false)
    public List<BillingInvoiceResponse> invoices(@RequestParam(required = false) String period) { access.requireSuperAdmin(); return billing.invoicesForPeriod(period); }

    @PostMapping("/tenants/{tenantId}/invoices") @RequirePermission(resource = Permission.Resource.BILLING, action = Permission.Action.CREATE, tenantScoped = false)
    public BillingInvoiceResponse generate(@PathVariable UUID tenantId, @RequestParam(required = false) String period) { access.requireSuperAdmin(); return billing.generate(tenantId, period); }

    @PutMapping("/invoices/{id}/status") @RequirePermission(resource = Permission.Resource.BILLING, action = Permission.Action.UPDATE, tenantScoped = false)
    public BillingInvoiceResponse status(@PathVariable UUID id, @Valid @RequestBody InvoiceStatusRequest request) { access.requireSuperAdmin(); return billing.setStatus(id, request.status()); }

    @PostMapping("/invoices/{id}/lock") @RequirePermission(resource = Permission.Resource.BILLING, action = Permission.Action.UPDATE, tenantScoped = false)
    public BillingInvoiceResponse lock(@PathVariable UUID id) { access.requireSuperAdmin(); return billing.lock(id); }
}
