package com.starsoft.voint.telegram;

import java.time.Duration;
import java.util.Map;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.starsoft.voint.settings.PlatformSettingsService;
import com.starsoft.voint.settings.SettingKey;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Thin wrapper over the Telegram Bot HTTP API - just the two calls this product needs. */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramClient {

    private final PlatformSettingsService settings;

    private final RestClient restClient = RestClient.builder()
            .requestFactory(requestFactory())
            .build();

    private static SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        return factory;
    }

    /** True if the message went out. Never throws - a Telegram outage must not break call handling. */
    public boolean sendMessage(long chatId, String text) {
        String token = settings.get(SettingKey.TELEGRAM_BOT_TOKEN);
        if (!StringUtils.hasText(token)) {
            return false;
        }
        try {
            restClient.post()
                    .uri("https://api.telegram.org/bot{token}/sendMessage", token)
                    .body(Map.of("chat_id", chatId, "text", text, "parse_mode", "HTML",
                            "link_preview_options", Map.of("is_disabled", true)))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.warn("Telegram sendMessage to chat {} failed: {}", chatId, e.getMessage());
            return false;
        }
    }

    /** The bot's @username, needed to build a {@code t.me/<username>?start=...} deep link. */
    public String getBotUsername() {
        String token = settings.get(SettingKey.TELEGRAM_BOT_TOKEN);
        if (!StringUtils.hasText(token)) {
            return null;
        }
        try {
            JsonNode body = restClient.get()
                    .uri("https://api.telegram.org/bot{token}/getMe", token)
                    .retrieve()
                    .body(JsonNode.class);
            String username = body != null ? body.path("result").path("username").asText(null) : null;
            return StringUtils.hasText(username) ? username : null;
        } catch (Exception e) {
            log.warn("Telegram getMe failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Points Telegram's webhook at this backend, with a secret token so incoming calls can be told
     * apart from anyone who guesses the URL (Telegram echoes it back in a header on every update).
     *
     * @throws TelegramSyncException if Telegram rejects the token or the URL
     */
    public void setWebhook(String botToken, String webhookUrl, String secretToken) {
        try {
            restClient.post()
                    .uri("https://api.telegram.org/bot{token}/setWebhook", botToken)
                    .body(Map.of("url", webhookUrl, "secret_token", secretToken,
                            "allowed_updates", new String[] {"message"}))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            throw new TelegramSyncException("Telegram webhook qeydə alınmadı: " + e.getMessage(), e);
        }
    }

    public static class TelegramSyncException extends RuntimeException {
        public TelegramSyncException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
