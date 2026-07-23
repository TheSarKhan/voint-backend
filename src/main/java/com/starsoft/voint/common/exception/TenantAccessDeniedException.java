package com.starsoft.voint.common.exception;

/**
 * Thrown when an authenticated panel user tries to access a tenant-scoped resource that
 * does not belong to their own tenant (and they are not a SUPER_ADMIN). Mapped to HTTP 403
 * (ProblemDetail).
 */
public class TenantAccessDeniedException extends RuntimeException {

    public TenantAccessDeniedException(String message) {
        super(message);
    }
}
