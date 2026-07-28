package com.starsoft.voint.lead;

import com.starsoft.voint.rbac.Permission;
import com.starsoft.voint.rbac.PublicEndpoint;
import com.starsoft.voint.rbac.RequirePermission;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.starsoft.voint.auth.TenantAccessGuard;
import com.starsoft.voint.common.dto.PageRequests;
import com.starsoft.voint.common.dto.PageResponse;
import com.starsoft.voint.lead.dto.LeadCreateRequest;
import com.starsoft.voint.lead.dto.LeadResponse;
import com.starsoft.voint.lead.dto.LeadUpdateRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Pilot requests from the public landing page.
 *
 * <p>Two different audiences, hence two different path prefixes. The write path lives under
 * {@code /api/v1/public/**}, which SecurityConfig already lets through unauthenticated - the
 * landing has no login and never will. Reading and triaging them is
 * {@code /api/v1/admin/**} and SUPER_ADMIN only: the submissions contain other people's phone
 * numbers, so a tenant panel user has no business seeing them.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Leads", description = "Pilot requests submitted from the landing page")
public class LeadController {

    private final LeadService leadService;
    private final LeadRateLimiter rateLimiter;
    private final TenantAccessGuard tenantAccessGuard;

    @RequirePermission(resource = Permission.Resource.LEAD, action = Permission.Action.READ, tenantScoped = false)
    @PostMapping("/api/v1/public/leads")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Submit a pilot request (public, rate limited)")
    public LeadResponse submit(@Valid @RequestBody LeadCreateRequest request,
                               HttpServletRequest http) {
        if (!rateLimiter.tryAcquire(clientIp(http))) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Çox sayda sorğu göndərilib. Bir qədər sonra yenidən cəhd edin.");
        }
        return LeadResponse.from(leadService.submit(request));
    }

    @RequirePermission(resource = Permission.Resource.LEAD, action = Permission.Action.READ, tenantScoped = false)
    @GetMapping("/api/v1/admin/leads")
    @Operation(summary = "List pilot requests, paginated (SUPER_ADMIN only)")
    public PageResponse<LeadResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) LeadStatus status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction) {
        tenantAccessGuard.requireSuperAdmin();
        Pageable pageable = PageRequests.of(page, size, sort, direction,
                LeadService.SORTABLE, LeadService.DEFAULT_SORT);
        return PageResponse.of(leadService.search(q, status, pageable), LeadResponse::from);
    }

    @RequirePermission(resource = Permission.Resource.LEAD, action = Permission.Action.READ, tenantScoped = false)
    @GetMapping("/api/v1/admin/leads/summary")
    @Operation(summary = "How many pilot requests are still untouched (SUPER_ADMIN only)")
    public Map<String, Long> summary() {
        tenantAccessGuard.requireSuperAdmin();
        return Map.of("newCount", leadService.countNew());
    }

    @RequirePermission(resource = Permission.Resource.LEAD, action = Permission.Action.READ, tenantScoped = false)
    @PatchMapping("/api/v1/admin/leads/{id}")
    @Operation(summary = "Update the status or note of a pilot request (SUPER_ADMIN only)")
    public LeadResponse update(@PathVariable UUID id,
                               @Valid @RequestBody LeadUpdateRequest request) {
        tenantAccessGuard.requireSuperAdmin();
        return LeadResponse.from(leadService.update(id, request));
    }

    /**
     * Behind nginx the socket address is always the proxy, so the real client is whatever
     * X-Forwarded-For carries. Only the first entry is ours; the rest can be forged by the caller.
     */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
