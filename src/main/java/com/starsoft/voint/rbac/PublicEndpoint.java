package com.starsoft.voint.rbac;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an endpoint that intentionally needs no permission: login, the Vapi webhook, the
 * pre-login lookups. Explicit so the startup check can tell "deliberately open" from "forgotten".
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PublicEndpoint {

    /** Why it is open, in a few words. Shows up in the startup report. */
    String value() default "";
}
