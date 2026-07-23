package com.starsoft.voint.voice;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Verifies that an incoming request to the Vapi custom-LLM webhook actually came from Vapi
 * (rather than an arbitrary caller that discovered the public URL, e.g. the ngrok tunnel).
 *
 * <p>Kept as an interface so the current shared-secret header check ({@link
 * SharedSecretVapiWebhookVerifier}) can later be swapped for HMAC-SHA256 signature verification
 * (Vapi also supports signing the raw body with a header like {@code x-vapi-signature}) without
 * touching the filter that calls it.
 */
public interface VapiWebhookVerifier {

    /**
     * Whether verification is actively enforced. False in the bootstrap stage, before a
     * {@code VAPI_WEBHOOK_SECRET} has been configured - in that case {@link #verify} is not
     * even called and every request is let through (with a one-time startup warning).
     */
    boolean isEnabled();

    /** Returns true if the request passes verification. Only meaningful when {@link #isEnabled()}. */
    boolean verify(HttpServletRequest request);
}
