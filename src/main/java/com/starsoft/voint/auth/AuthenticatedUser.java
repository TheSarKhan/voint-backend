package com.starsoft.voint.auth;

import java.util.UUID;

import org.springframework.security.core.AuthenticatedPrincipal;

/**
 * Authentication principal built from the panel JWT's claims. Carries the
 * fields tenant-scoped controllers need to enforce access control ({@link #tenantId()},
 * {@link #role()}) in addition to the email.
 * <p>
 * Implements {@link AuthenticatedPrincipal} so that {@code Authentication.getName()}
 * (and thus {@code Principal.getName()} on the object Spring MVC injects into
 * {@code @RequestMapping} methods, e.g. {@code AuthController.me(Principal)}) keeps
 * returning the user's email exactly as it did when the principal was a bare String.
 * <p>
 * {@code tenantId} is nullable: a platform-wide {@code SUPER_ADMIN} user is not scoped
 * to any single tenant.
 */
public record AuthenticatedUser(String email, UUID tenantId, String role) implements AuthenticatedPrincipal {

    @Override
    public String getName() {
        return email;
    }

    @Override
    public String toString() {
        return email;
    }

    public boolean isSuperAdmin() {
        return "SUPER_ADMIN".equals(role);
    }
}
