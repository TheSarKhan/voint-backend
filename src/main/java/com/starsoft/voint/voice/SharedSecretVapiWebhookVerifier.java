package com.starsoft.voint.voice;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * Verifies the Vapi "Server URL Secret" mechanism: Vapi is configured (in the Vapi dashboard, per
 * assistant/phone number) with a shared secret that it echoes back verbatim on every webhook
 * request via the {@value #SECRET_HEADER} header. We compare it against the configured
 * {@code voint.vapi.webhook-secret} (env {@code VAPI_WEBHOOK_SECRET}) using a constant-time
 * comparison so response timing can't leak the secret.
 */
@Slf4j
@Component
public class SharedSecretVapiWebhookVerifier implements VapiWebhookVerifier {

    /** Header Vapi sends the configured "Server URL Secret" back on, verbatim, on every webhook call. */
    public static final String SECRET_HEADER = "x-vapi-secret";

    @Value("${voint.vapi.webhook-secret:}")
    private String configuredSecret;

    @PostConstruct
    void logIfDisabled() {
        if (!isEnabled()) {
            log.warn("VAPI_WEBHOOK_SECRET is not set - Vapi webhook signature verification is DISABLED. "
                    + "Any request reaching /api/v1/voice/webhook (including over a public ngrok tunnel) will "
                    + "be accepted without proof it came from Vapi. Set VAPI_WEBHOOK_SECRET (the 'Server URL "
                    + "Secret' from the Vapi dashboard) before exposing this backend publicly.");
        }
    }

    @Override
    public boolean isEnabled() {
        return configuredSecret != null && !configuredSecret.isBlank();
    }

    @Override
    public boolean verify(HttpServletRequest request) {
        if (!isEnabled()) {
            return true;
        }
        String provided = request.getHeader(SECRET_HEADER);
        if (provided == null || provided.isEmpty()) {
            return false;
        }
        byte[] providedBytes = provided.getBytes(StandardCharsets.UTF_8);
        byte[] expectedBytes = configuredSecret.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(providedBytes, expectedBytes);
    }
}
