package com.starsoft.voint.rbac;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * What a caller must be allowed to do to reach this endpoint.
 *
 * <p>Declared at the endpoint rather than checked inside it, so the requirement is visible in the
 * same glance as the route, and so one interceptor enforces every rule the same way. A startup
 * check reports any endpoint that carries neither this nor {@link PublicEndpoint} - the failure
 * mode being guarded against is a new endpoint quietly shipping with no authorisation at all.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    Permission.Resource resource();

    Permission.Action action();

    /**
     * When true, a tenant-scoped caller may only act on its own tenant. The interceptor reads the
     * {@code tenantId} path variable and compares. Platform staff pass regardless.
     */
    boolean tenantScoped() default true;
}
