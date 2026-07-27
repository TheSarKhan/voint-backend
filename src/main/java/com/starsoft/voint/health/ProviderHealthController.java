package com.starsoft.voint.health;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.starsoft.voint.auth.TenantAccessGuard;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@Tag(name = "Provider health", description = "State of the third-party services a call depends on")
public class ProviderHealthController {

    private final ProviderHealthService providerHealthService;
    private final TenantAccessGuard tenantAccessGuard;

    @GetMapping("/api/v1/admin/providers")
    @Operation(summary = "Health of ElevenLabs / Gemini credentials (SUPER_ADMIN only)")
    public List<ProviderHealth> providers() {
        tenantAccessGuard.requireSuperAdmin();
        return providerHealthService.current();
    }
}
