package com.starsoft.voint.telegram;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.starsoft.voint.settings.PlatformSettingsService;
import com.starsoft.voint.settings.SettingKey;

import lombok.extern.slf4j.Slf4j;

/**
 * Points Telegram's webhook at this backend whenever the bot token is (re)saved. Mirrors
 * {@code VapiSyncService}: one place a credential is set, one place it takes effect everywhere
 * that needs a copy of it.
 */
@Slf4j
@Service
public class TelegramSyncService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final PlatformSettingsService settings;
    private final TelegramClient client;
    private final String publicBaseUrl;

    public TelegramSyncService(PlatformSettingsService settings, TelegramClient client,
            @Value("${voint.public-base-url:https://voint.sarkhan.az}") String publicBaseUrl) {
        this.settings = settings;
        this.client = client;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    /**
     * Generates a webhook secret if none exists yet, then registers (or re-registers) the webhook
     * with Telegram. Safe to call every time the bot token is saved, even if nothing changed.
     */
    public void registerWebhook() {
        String botToken = settings.get(SettingKey.TELEGRAM_BOT_TOKEN);
        if (!StringUtils.hasText(botToken)) {
            throw new TelegramClient.TelegramSyncException("Bot tokeni boşdur", null);
        }
        String secret = settings.get(SettingKey.TELEGRAM_WEBHOOK_SECRET);
        if (!StringUtils.hasText(secret)) {
            secret = generateSecret();
            settings.set(SettingKey.TELEGRAM_WEBHOOK_SECRET, secret, "system");
        }
        client.setWebhook(botToken, publicBaseUrl + "/api/v1/telegram/webhook", secret);
        log.info("Telegram webhook registered at {}/api/v1/telegram/webhook", publicBaseUrl);
    }

    /** URL-safe, no padding - travels as an HTTP header value with no escaping to worry about. */
    private String generateSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
