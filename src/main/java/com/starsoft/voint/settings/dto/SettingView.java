package com.starsoft.voint.settings.dto;

import java.time.Instant;

/**
 * One credential as the admin panel sees it.
 *
 * <p>{@code hint} is a masked fingerprint (last four characters), never the value: a secret that
 * leaves the server to be rendered in a browser is a secret in one more place than it needs to be.
 * The operator only needs enough to answer "is this the key I think it is".
 */
public record SettingView(
        String key,
        String label,
        String description,
        boolean secret,
        /** Whether any value is in effect at all. */
        boolean configured,
        /** True when the value comes from this panel, false when it falls back to the server config. */
        boolean managedHere,
        /** e.g. "…b471", or the plain value for non-secret keys like the voice id. */
        String hint,
        Instant updatedAt,
        String updatedBy
) {
}
