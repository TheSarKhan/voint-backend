package com.starsoft.voint.rbac;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import com.starsoft.voint.auth.AuthenticatedUser;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Enforces {@link RequirePermission} before a controller method runs.
 *
 * <p>Two checks, in this order:
 * <ol>
 *   <li><b>Which tenant</b> - a business may only touch its own data. Checked first because
 *       "wrong tenant" is a harder boundary than "wrong permission": leaking another company's
 *       calls is worse than letting someone read their own.</li>
 *   <li><b>Which action</b> - does the caller's role grant this resource/action pair.</li>
 * </ol>
 *
 * <p>Endpoints with no annotation are left alone here and reported at startup instead - failing
 * them closed would take the whole panel down for a single missed annotation, and failing them
 * open silently is what this is meant to prevent. The report is the middle path: loud, visible,
 * and fixable before it matters.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionInterceptor implements HandlerInterceptor {

    private final PermissionResolver permissions;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        RequirePermission required = method.getMethodAnnotation(RequirePermission.class);
        if (required == null) {
            return true;
        }

        AuthenticatedUser user = currentUser();
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Giriş tələb olunur");
        }

        // Read the role and status from the database, not from the token. A JWT lives an hour,
        // so trusting its claims would let a blocked account keep working for an hour after
        // being blocked - which is not blocking.
        var account = permissions.currentUser(user.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Hesab tapılmadı"));

        if (account.blocked()) {
            log.warn("Blocked account {} attempted {} {}", user.email(),
                    request.getMethod(), request.getRequestURI());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Hesabınız bloklanıb");
        }

        if (required.tenantScoped()) {
            requireOwnTenant(request, user);
        }

        // Platform staff are not bound by the tenant check, but they still need the permission:
        // a support account with read-only access must not be able to delete a business.
        if (!permissions.isAllowed(account.roleId(), required.resource(), required.action())) {
            log.warn("Denied {} {} for {} - role {} lacks {}:{}",
                    request.getMethod(), request.getRequestURI(), user.email(), account.roleId(),
                    required.resource(), required.action());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Bu əməliyyat üçün icazəniz yoxdur: "
                            + required.resource().getLabel() + " · " + required.action().getLabel());
        }
        return true;
    }

    /**
     * A caller tied to a business may act only on that business. Platform staff (no tenant on the
     * account) pass, because managing every tenant is precisely their job.
     */
    private void requireOwnTenant(HttpServletRequest request, AuthenticatedUser user) {
        if (user.tenantId() == null) {
            return;
        }
        Object vars = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (!(vars instanceof Map<?, ?> map)) {
            return;
        }
        // Bes controller yol deyisenini "id" adlandirir (/api/v1/tenants/{id}/calls), biri ise
        // "tenantId". Yalniz birine baxsaq, mudafienin bu qati onlarin yarisinda islemez —
        // controller-in icindeki requireAccess hele de qoruyur, amma yeni endpoint onu unudarsa
        // hec ne qalmir.
        Object raw = map.get("tenantId");
        if (raw == null && request.getRequestURI().startsWith("/api/v1/tenants/")) {
            raw = map.get("id");
        }
        if (raw == null) {
            return;
        }
        try {
            if (!user.tenantId().equals(UUID.fromString(raw.toString()))) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Bu müəssisəyə girişiniz yoxdur");
            }
        } catch (IllegalArgumentException notAUuid) {
            // Addressed by subdomain rather than id; the controller resolves and guards it there.
            log.debug("tenantId path variable '{}' is not a UUID - leaving it to the controller", raw);
        }
    }

    private AuthenticatedUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AuthenticatedUser u ? u : null;
    }
}
