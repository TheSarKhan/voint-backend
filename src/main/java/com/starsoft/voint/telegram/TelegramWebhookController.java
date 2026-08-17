package com.starsoft.voint.telegram;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.starsoft.voint.rbac.PublicEndpoint;
import com.starsoft.voint.settings.PlatformSettingsService;
import com.starsoft.voint.settings.SettingKey;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Telegram's webhook - what the bot receives when a tenant messages it. The only thing this
 * product needs from it is {@code /start <token>}, which finishes the linking flow started by
 * {@link TelegramLinkController#createLink}.
 *
 * <p>{@code permitAll} at the Spring Security level (Telegram's servers cannot present a panel
 * JWT) but self-verifies {@code X-Telegram-Bot-Api-Secret-Token} against the value {@code
 * TelegramSyncService} registered with Telegram - the same shape as the Vapi webhook, just
 * checked inline since there is only the one path here.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Telegram", description = "Telegram's own webhook - not called by the panels")
public class TelegramWebhookController {

    private final TelegramLinkService linkService;
    private final TelegramChatRepository chats;
    private final TelegramClient client;
    private final PlatformSettingsService settings;

    @PublicEndpoint("Telegram's servers cannot present a panel JWT - verified via secret header instead")
    @PostMapping("/api/v1/telegram/webhook")
    @Operation(summary = "Telegram update webhook")
    public void webhook(@RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String secret,
                        @RequestBody JsonNode update) {
        String expected = settings.get(SettingKey.TELEGRAM_WEBHOOK_SECRET);
        if (!StringUtils.hasText(expected) || !expected.equals(secret)) {
            log.warn("Rejected Telegram webhook request - missing/invalid secret header");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        JsonNode message = update.path("message");
        String text = message.path("text").asText(null);
        if (text == null || !text.startsWith("/start")) {
            return; // Not a linking attempt - nothing else needs handling yet.
        }

        long chatId = message.path("chat").path("id").asLong(0);
        if (chatId == 0) {
            return;
        }

        String token = text.length() > 6 ? text.substring(6).trim() : "";
        if (token.isEmpty()) {
            client.sendMessage(chatId, "Salam! Bu bot Voint zəng bildirişləri üçündür. "
                    + "Qoşulmaq üçün panelinizdəki Ayarlar səhifəsindən link alın.");
            return;
        }

        UUID tenantId = linkService.consume(token);
        if (tenantId == null) {
            client.sendMessage(chatId, "Bu link artıq etibarsızdır (15 dəqiqədən sonra bitir). "
                    + "Panelinizdən yeni link alın.");
            return;
        }

        if (chats.findByTenantIdAndChatId(tenantId, chatId).isEmpty()) {
            chats.save(TelegramChat.builder()
                    .tenantId(tenantId)
                    .chatId(chatId)
                    .label(chatLabel(message.path("chat")))
                    .build());
        }
        client.sendMessage(chatId, "✅ Qoşuldu! Bundan sonra zəng nəticələri bura gələcək.");
    }

    /** Prefers a group's title; falls back to a person's name for a private chat. */
    private String chatLabel(JsonNode chat) {
        String title = chat.path("title").asText(null);
        if (StringUtils.hasText(title)) {
            return title;
        }
        String first = chat.path("first_name").asText("");
        String last = chat.path("last_name").asText("");
        String name = (first + " " + last).trim();
        return name.isEmpty() ? null : name;
    }
}
