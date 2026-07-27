package com.starsoft.voint.health;

import java.time.Instant;

/**
 * Last known state of one external provider we depend on to answer a phone call.
 *
 * @param name      provider name as shown to the operator
 * @param status    OK, DOWN, or NOT_CONFIGURED
 * @param detail    human-readable reason - what to do about it, when something is wrong
 * @param checkedAt when this was last verified
 */
public record ProviderHealth(String name, Status status, String detail, Instant checkedAt) {

    public enum Status {
        /** Provider answered and accepted our credentials. */
        OK,
        /** Provider rejected us or could not be reached - calls will fail. */
        DOWN,
        /** No credential configured, so nothing was checked. */
        NOT_CONFIGURED
    }

    public static ProviderHealth ok(String name, String detail) {
        return new ProviderHealth(name, Status.OK, detail, Instant.now());
    }

    public static ProviderHealth down(String name, String detail) {
        return new ProviderHealth(name, Status.DOWN, detail, Instant.now());
    }

    public static ProviderHealth notConfigured(String name, String detail) {
        return new ProviderHealth(name, Status.NOT_CONFIGURED, detail, Instant.now());
    }
}
