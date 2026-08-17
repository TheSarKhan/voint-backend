package com.starsoft.voint.telegram;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * Short-lived tenant<->Telegram linking tokens. Redis, not a table: a token only ever needs to
 * survive the couple of minutes between "show me the link" and the tenant tapping /start in
 * Telegram, and letting it expire on its own is simpler than a cleanup job for a row nobody reads
 * again.
 */
@Service
@RequiredArgsConstructor
public class TelegramLinkService {

    private static final Duration TTL = Duration.ofMinutes(15);
    private static final String PREFIX = "voint:telegram-link:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;
    private final TelegramClient client;

    /** A fresh one-time token for this tenant, good for 15 minutes. */
    public String createToken(UUID tenantId) {
        String token = randomToken();
        redis.opsForValue().set(PREFIX + token, tenantId.toString(), TTL);
        return token;
    }

    /** {@code https://t.me/<bot>?start=<token>} - opens a private chat with the bot and links it. */
    public String buildDeepLink(String token) {
        String username = client.getBotUsername();
        return username != null ? "https://t.me/" + username + "?start=" + token : null;
    }

    /**
     * {@code startgroup}, not {@code start}: this is what makes Telegram show a "choose a group to
     * add this bot to" picker instead of opening a private chat. Once added, Telegram delivers the
     * same token to the webhook as if someone had typed {@code /start <token>} in that group - no
     * extra handling needed on our side, {@link TelegramWebhookController} already treats both the
     * same way.
     */
    public String buildGroupDeepLink(String token) {
        String username = client.getBotUsername();
        return username != null ? "https://t.me/" + username + "?startgroup=" + token : null;
    }

    /** Consumes the token (single-use) and returns the tenant it belonged to, if it was still valid. */
    public UUID consume(String token) {
        String raw = redis.opsForValue().getAndDelete(PREFIX + token);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String randomToken() {
        byte[] bytes = new byte[18];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
