package com.starsoft.voint.analytics;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
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

    @GetMapping
    @Operation(summary = "Call count, resolution rate and average duration for the tenant")
    public AnalyticsResponse analytics(@PathVariable("id") UUID tenantId) {
        tenantAccessGuard.requireAccess(tenantId);
        return analyticsService.forTenant(tenantId);
    }
}
