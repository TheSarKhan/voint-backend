package com.starsoft.voint.approval;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.starsoft.voint.auth.AuthenticatedUser;
import com.starsoft.voint.rbac.Permission;
import com.starsoft.voint.rbac.RequirePermission;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Holds a business's changes and deletions until somebody approves them.
 *
 * <p>Runs after the permission check on purpose: you cannot queue up an operation you were not
 * allowed to perform in the first place. Approval is a second gate, not a way around the first.
 *
 * <p>Platform staff are not held. They have no tenant to be approved by, and gating them would mean
 * Voint cannot fix a customer's account without that customer's permission - which is not a control,
 * it is an outage waiting for office hours.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalGateInterceptor implements HandlerInterceptor {

    private static final Set<String> HELD_METHODS = Set.of("PUT", "PATCH", "DELETE");

    /**
     * Paths that must never be held.
     *
     * <p>The approval endpoints above all: if deciding on a request itself needed a decision, the
     * first held operation would freeze the queue permanently and nothing could ever unfreeze it.
     */
    private static final Set<String> NEVER_HELD_PREFIXES = Set.of(
            "/api/v1/auth/",
            "/api/v1/voice/");

    private final ApprovalService approvals;
    private final com.starsoft.voint.rbac.PermissionResolver permissionResolver;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        if (!HELD_METHODS.contains(request.getMethod())) {
            return true;
        }
        if (request.getAttribute(ApprovalReplayFilter.REPLAY_ATTRIBUTE) != null) {
            return true;
        }

        String path = request.getRequestURI();
        if (path.contains("/approvals") || NEVER_HELD_PREFIXES.stream().anyMatch(path::startsWith)) {
            return true;
        }

        AuthenticatedUser user = currentUser();
        if (user == null || user.tenantId() == null) {
            return true;
        }

        // Direct execution check: if the user's role has APPROVAL · CREATE (Direct execution permission), bypass hold!
        var cachedUser = permissionResolver.currentUser(user.email()).orElse(null);
        if (cachedUser != null && cachedUser.roleId() != null) {
            if (permissionResolver.isAllowed(cachedUser.roleId(), Permission.Resource.APPROVAL, Permission.Action.CREATE)) {
                return true;
            }
        }

        RequirePermission required = method.getMethodAnnotation(RequirePermission.class);
        if (required == null) {
            // Unannotated endpoints are reported at startup by EndpointCoverageReporter. Holding
            // one without knowing what it touches would produce a queue entry nobody can judge.
            return true;
        }

        ApprovalRequest held = approvals.hold(user, request, required.resource(), required.action());

        response.setStatus(HttpStatus.ACCEPTED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("""
                {"status":"PENDING_APPROVAL","approvalId":"%s","detail":"%s"}"""
                .formatted(held.getId(),
                        "Əməliyyat təsdiq gözləyir. Təsdiqlənənə qədər heç nə dəyişmir."));
        log.info("Held {} {} from {} as approval {}",
                request.getMethod(), path, user.email(), held.getId());
        return false;
    }

    private AuthenticatedUser currentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AuthenticatedUser u ? u : null;
    }

    /** Exposed for the summary line; kept here so the vocabulary stays in one place. */
    static String describe(Permission.Resource resource, Permission.Action action) {
        return resource.getLabel() + " · " + action.getLabel();
    }
}
