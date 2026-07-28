package com.starsoft.voint.approval;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.starsoft.voint.approval.dto.ApprovalResponse;
import com.starsoft.voint.auth.AuthenticatedUser;
import com.starsoft.voint.auth.TenantAccessGuard;
import com.starsoft.voint.rbac.Permission;
import com.starsoft.voint.rbac.RequirePermission;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * The approval queue of one business.
 *
 * <p>Deciding is a POST rather than a PUT, and not by accident: {@link ApprovalGateInterceptor}
 * holds PUT and DELETE, so a queue managed with those verbs would need itself approved and the
 * first held operation would freeze everything permanently.
 */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/approvals")
@RequiredArgsConstructor
@Tag(name = "Approvals", description = "Changes and deletions waiting to be authorised")
public class ApprovalController {

    private final ApprovalService approvals;
    private final TenantAccessGuard tenantAccessGuard;

    @RequirePermission(resource = Permission.Resource.APPROVAL, action = Permission.Action.READ)
    @GetMapping
    @Operation(summary = "Requests in this business, newest first")
    public List<ApprovalResponse> list(@PathVariable UUID tenantId,
                                       @RequestParam(required = false) String status) {
        tenantAccessGuard.requireAccess(tenantId);
        return approvals.list(tenantId, status);
    }

    /** Drives the badge in the panel's navigation; cheap enough to poll. */
    @RequirePermission(resource = Permission.Resource.APPROVAL, action = Permission.Action.READ)
    @GetMapping("/pending-count")
    @Operation(summary = "How many are still waiting")
    public Map<String, Long> pendingCount(@PathVariable UUID tenantId) {
        tenantAccessGuard.requireAccess(tenantId);
        return Map.of("pending", approvals.pendingCount(tenantId));
    }

    @RequirePermission(resource = Permission.Resource.APPROVAL, action = Permission.Action.UPDATE)
    @PostMapping("/{id}/approve")
    @Operation(summary = "Authorise it and carry it out")
    public ApprovalResponse approve(@PathVariable UUID tenantId, @PathVariable UUID id,
                                    @RequestBody(required = false) Map<String, String> body) {
        tenantAccessGuard.requireAccess(tenantId);
        return approvals.approve(tenantId, id, currentUser(), note(body));
    }

    @RequirePermission(resource = Permission.Resource.APPROVAL, action = Permission.Action.UPDATE)
    @PostMapping("/{id}/reject")
    @Operation(summary = "Refuse it; nothing is carried out")
    public ApprovalResponse reject(@PathVariable UUID tenantId, @PathVariable UUID id,
                                   @RequestBody(required = false) Map<String, String> body) {
        tenantAccessGuard.requireAccess(tenantId);
        return approvals.reject(tenantId, id, currentUser(), note(body));
    }

    private String note(Map<String, String> body) {
        return body == null ? null : body.get("note");
    }

    private AuthenticatedUser currentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AuthenticatedUser u ? u : null;
    }
}
