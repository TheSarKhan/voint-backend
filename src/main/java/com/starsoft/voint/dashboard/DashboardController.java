package com.starsoft.voint.dashboard;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.starsoft.voint.dashboard.dto.DashboardResponse;
import com.starsoft.voint.rbac.Permission;
import com.starsoft.voint.rbac.RequirePermission;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Platform-wide overview for the admin panel's landing page.
 *
 * <p>Gated on TENANT:READ, the same permission the tenant list itself requires - this is that same
 * "which businesses do we run" view, just summarised instead of listed row by row.
 */
@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Platform-wide overview (SUPER_ADMIN only)")
public class DashboardController {

    private final DashboardService dashboardService;

    @RequirePermission(resource = Permission.Resource.TENANT, action = Permission.Action.READ, tenantScoped = false)
    @GetMapping
    @Operation(summary = "Platform-wide KPIs, trend, recent calls and top tenants")
    public DashboardResponse get() {
        return dashboardService.get();
    }
}
