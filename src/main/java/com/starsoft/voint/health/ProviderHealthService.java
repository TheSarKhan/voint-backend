package com.starsoft.voint.health;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.starsoft.voint.settings.PlatformSettingsService;
import com.starsoft.voint.settings.SettingKey;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Periodically verifies the third-party credentials a phone call depends on.
 *
 * <p>Written after an ElevenLabs key was revoked without warning: the first sign of trouble was a
 * caller hearing silence, because nothing in the system ever checked whether the key still worked.
 *
 * <p>Note the asymmetry that made it invisible - the ElevenLabs key is used by Vapi, not by this
 * backend, so no amount of normal traffic here would ever have exercised it. That is exactly why
 * it needs an explicit check.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderHealthService {

    private static final String ELEVENLABS = "ElevenLabs";
    private static final String GEMINI = "Gemini";
    private static final String VAPI = "Vapi";
    private static final String TELEGRAM = "Telegram";

    private final ProviderProbe probe;
    private final PlatformSettingsService settings;

    private final Map<String, ProviderHealth> state = new ConcurrentHashMap<>();

    /** Current snapshot, in the order an operator wants to scan it. */
    public List<ProviderHealth> current() {
        return List.of(snapshot(ELEVENLABS), snapshot(GEMINI), snapshot(VAPI), snapshot(TELEGRAM));
    }

    private ProviderHealth snapshot(String name) {
        return state.getOrDefault(name, ProviderHealth.notConfigured(name, "Hələ yoxlanılmayıb"));
    }

    /**
     * Runs shortly after startup and then every five minutes - short enough to catch a broken
     * credential before most business-hours traffic, few enough calls to be free.
     */
    @Scheduled(initialDelay = 30_000, fixedDelay = 300_000)
    public void check() {
        String elevenKey = settings.get(SettingKey.ELEVENLABS_API_KEY);
        String voiceId = settings.get(SettingKey.ELEVENLABS_VOICE_ID);
        record(ELEVENLABS, elevenKey, () -> probe.elevenLabs(elevenKey, voiceId),
                "VOINT_ELEVENLABS_API_KEY və ya Ayarlar səhifəsi");

        String geminiKey = settings.get(SettingKey.GEMINI_API_KEY);
        record(GEMINI, geminiKey, () -> probe.gemini(geminiKey), "GEMINI_API_KEY");

        String vapiKey = settings.get(SettingKey.VAPI_PRIVATE_KEY);
        record(VAPI, vapiKey, () -> probe.vapi(vapiKey), "VAPI_PRIVATE_KEY");

        String telegramToken = settings.get(SettingKey.TELEGRAM_BOT_TOKEN);
        record(TELEGRAM, telegramToken, () -> probe.telegram(telegramToken), "Ayarlar səhifəsi");
    }

    private void record(String name, String credential, java.util.function.Supplier<ProviderProbe.Result> check,
                        String whereToSet) {
        ProviderHealth health;
        if (!StringUtils.hasText(credential)) {
            health = ProviderHealth.notConfigured(name, "Açar təyin olunmayıb (" + whereToSet + ")");
        } else {
            ProviderProbe.Result result = check.get();
            health = result.ok()
                    ? ProviderHealth.ok(name, result.detail())
                    : ProviderHealth.down(name, result.detail());
        }

        ProviderHealth previous = state.put(name, health);
        // Log transitions only: a healthy system stays quiet, a break is loud.
        if (previous != null && previous.status() == health.status()) {
            return;
        }
        if (health.status() == ProviderHealth.Status.DOWN) {
            log.error("PROVIDER DOWN - {}: {}. Calls depending on it will fail until this is fixed.",
                    name, health.detail());
        } else {
            log.info("Provider {} is now {}: {}", name, health.status(), health.detail());
        }
    }

    /** Re-probes immediately, so the panel reflects a just-saved key without waiting five minutes. */
    public List<ProviderHealth> refresh() {
        check();
        return current();
    }
}
