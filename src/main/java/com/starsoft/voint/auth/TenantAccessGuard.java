package com.starsoft.voint.auth;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.starsoft.voint.common.exception.TenantAccessDeniedException;

/**
 * Single, reusable enforcement point for the tenant-scoping rule: a panel user may only
 * access the tenant path-variable that matches their own JWT-carried tenantId, UNLESS
 * their role is SUPER_ADMIN, in which case any tenant is allowed.
 * <p>
 * Called at the top of every method in the six tenant-scoped controllers
 * (TenantController, CallController, CustomerController, RagController,
 * ReservationController, AnalyticsController) instead of ad-hoc per-method checks.
 */
@Component
public class TenantAccessGuard {

    /** Requires the current user to either be a SUPER_ADMIN or own the given tenant. */
    public void requireAccess(UUID tenantId) {
        AuthenticatedUser user = currentUser();
        if (user.isSuperAdmin()) {
            return;
        }
        if (user.tenantId() == null || !user.tenantId().equals(tenantId)) {
            throw new TenantAccessDeniedException(
                    "You do not have access to tenant " + tenantId);
        }
    }

    /** Requires the current user to be a platform-wide SUPER_ADMIN. */
    public void requireSuperAdmin() {
        AuthenticatedUser user = currentUser();
        if (!user.isSuperAdmin()) {
            throw new TenantAccessDeniedException("SUPER_ADMIN role required");
        }
    }

    private AuthenticatedUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new TenantAccessDeniedException("Not authenticated");
        }
        return user;
    }
}
