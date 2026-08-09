package com.starsoft.voint.billing;

import java.util.*;
import org.springframework.web.bind.annotation.*;
import com.starsoft.voint.auth.TenantAccessGuard;
import com.starsoft.voint.billing.dto.BillingInvoiceResponse;
import com.starsoft.voint.rbac.*;
import lombok.RequiredArgsConstructor;

/** Read-only history in the tenant route: platform users may inspect it while tenant users can see their own. */
@RestController @RequiredArgsConstructor
@RequestMapping("/api/v1/tenants/{tenantId}/invoices")
public class TenantBillingController {
    private final BillingService billing;
    private final TenantAccessGuard access;
    @GetMapping @RequirePermission(resource = Permission.Resource.BILLING, action = Permission.Action.READ)
    public List<BillingInvoiceResponse> list(@PathVariable UUID tenantId) { access.requireAccess(tenantId); return billing.invoicesFor(tenantId); }
}
