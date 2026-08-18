package com.starsoft.voint.analytics;

import com.starsoft.voint.rbac.Permission;
import com.starsoft.voint.rbac.PublicEndpoint;
import com.starsoft.voint.rbac.RequirePermission;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.starsoft.voint.analytics.dto.AnalyticsResponse;
import com.starsoft.voint.auth.TenantAccessGuard;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/tenants/{id}/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Runtime-computed call analytics (no snapshot table)")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final TenantAccessGuard tenantAccessGuard;

    @RequirePermission(resource = Permission.Resource.CALL, action = Permission.Action.READ)
    @GetMapping
    @Operation(summary = "Call count, resolution rate and average duration for the tenant, "
            + "over the last 7/30/90 days (defaults to 30)")
    public AnalyticsResponse analytics(@PathVariable("id") UUID tenantId,
                                       @RequestParam(required = false) Integer days) {
        tenantAccessGuard.requireAccess(tenantId);
        return analyticsService.forTenant(tenantId, days);
    }
}
