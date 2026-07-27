package com.starsoft.voint.tenant;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * The one thing a panel needs to know before anyone has logged in: which business this address
 * belongs to, so the login screen can say its name instead of a generic one.
 *
 * <p>Unauthenticated by necessity, so it returns the bare minimum. Name and id only - no phone
 * numbers, no greeting, no configuration. All it confirms is that a subdomain exists, which is
 * already implied by the address resolving at all.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Public", description = "Unauthenticated lookups used before login")
public class PublicTenantController {

    private final TenantRepository tenantRepository;

    /** What a panel is allowed to know about its tenant before login. */
    public record PublicTenant(UUID id, String name, String subdomain) {
    }

    @GetMapping("/api/v1/public/tenants/by-subdomain/{subdomain}")
    @Operation(summary = "Resolve a panel subdomain to the business it belongs to")
    public PublicTenant bySubdomain(@PathVariable String subdomain) {
        String normalized = subdomain == null ? "" : subdomain.trim().toLowerCase();
        return tenantRepository.findBySubdomainIgnoreCase(normalized)
                .map(t -> new PublicTenant(t.getId(), t.getName(), t.getSubdomain()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Bu ünvanda müəssisə tapılmadı"));
    }
}
