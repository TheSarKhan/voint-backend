package com.starsoft.voint.telegram;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.starsoft.voint.call.Call;
import com.starsoft.voint.call.CallStatus;
import com.starsoft.voint.settings.PlatformSettingsService;
import com.starsoft.voint.settings.SettingKey;
import com.starsoft.voint.tenant.Tenant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Sends the "a call just ended" notification to every Telegram chat a tenant has linked.
 *
 * <p>Best-effort throughout, deliberately: this runs right after a call is recorded, and a
 * Telegram outage (or a tenant with no chats linked) must never be why a call itself fails to
 * save. See {@code TelegramClient.sendMessage}, which already never throws.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramNotifier {

    private final TelegramChatRepository chats;
    private final TelegramClient client;
    private final PlatformSettingsService settings;

    public void notifyCallEnded(Tenant tenant, Call call, String aiSummary) {
        if (tenant == null) {
            return;
        }
        List<TelegramChat> linked = chats.findByTenantIdOrderByLinkedAtDesc(tenant.getId());
        if (linked.isEmpty()) {
            return;
        }
        String text = buildMessage(tenant, call, aiSummary);
        for (TelegramChat chat : linked) {
            client.sendMessage(chat.getChatId(), text);
        }
    }

    private String buildMessage(Tenant tenant, Call call, String aiSummary) {
        StringBuilder sb = new StringBuilder();
        sb.append("📞 <b>Yeni zəng</b>\n");
        sb.append(orDash(call.getCallerNumber())).append(" · ").append(formatDuration(call.getDurationSeconds()));
        sb.append(" · ").append(outcomeLabel(call.getStatus())).append('\n');
        if (StringUtils.hasText(aiSummary)) {
            sb.append('\n').append(aiSummary.trim()).append('\n');
        }
        String panelUrl = panelCallUrl(tenant, call.getId());
        if (panelUrl != null) {
            sb.append('\n').append(panelUrl);
        }
        return sb.toString();
    }

    private String outcomeLabel(CallStatus status) {
        if (status == null) {
            return "Naməlum";
        }
        return switch (status) {
            case HANDOFF -> "Operatora yönləndirildi";
            case ONGOING -> "Davam edir";
            case RESOLVED -> "Cavablandı";
        };
    }

    private String formatDuration(Integer seconds) {
        if (seconds == null || seconds < 0) {
            return "-";
        }
        return "%d:%02d".formatted(seconds / 60, seconds % 60);
    }

    /** Same domain scheme as the tenant's own panel login: {@code <subdomain>.<panel-domain>}. */
    private String panelCallUrl(Tenant tenant, UUID callId) {
        if (!StringUtils.hasText(tenant.getSubdomain())) {
            return null;
        }
        String panelDomain = settings.get(SettingKey.PANEL_DOMAIN);
        if (!StringUtils.hasText(panelDomain)) {
            return null;
        }
        return "https://" + tenant.getSubdomain() + "." + panelDomain + "/calls/" + callId;
    }

    private String orDash(String value) {
        return StringUtils.hasText(value) ? value : "-";
    }
}
