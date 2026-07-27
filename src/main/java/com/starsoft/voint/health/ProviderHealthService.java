package com.starsoft.voint.health;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.starsoft.voint.llm.GeminiApiClient;

import lombok.extern.slf4j.Slf4j;

/**
 * Periodically verifies the third-party credentials a phone call depends on.
 *
 * <p>Written after an ElevenLabs key was revoked without warning: the first sign of trouble was a
 * caller hearing silence, because nothing in the system ever checked whether the key still worked.
 * A dead credential is now visible in the admin panel and shouted into the logs within minutes,
 * instead of being discovered by a customer.
 *
 * <p>Note the asymmetry: the ElevenLabs key is used by Vapi, not by this backend, so nothing here
 * would ever have exercised it organically. That is exactly why it needs an explicit check.
 */
@Slf4j
@Service
public class ProviderHealthService {

    private static final String ELEVENLABS = "ElevenLabs";
    private static final String GEMINI = "Gemini";

    private final RestClient restClient;
    private final GeminiApiClient geminiApiClient;
    private final String elevenLabsApiKey;
    private final String elevenLabsVoiceId;

    private final Map<String, ProviderHealth> state = new ConcurrentHashMap<>();

    public ProviderHealthService(GeminiApiClient geminiApiClient,
                                 @Value("${voint.elevenlabs.api-key:}") String elevenLabsApiKey,
                                 @Value("${voint.elevenlabs.voice-id:}") String elevenLabsVoiceId) {
        this.geminiApiClient = geminiApiClient;
        this.elevenLabsApiKey = elevenLabsApiKey;
        this.elevenLabsVoiceId = elevenLabsVoiceId;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /** Current snapshot, in the order an operator wants to scan it. */
    public List<ProviderHealth> current() {
        return List.of(
                state.getOrDefault(ELEVENLABS,
                        ProviderHealth.notConfigured(ELEVENLABS, "Hələ yoxlanılmayıb")),
                state.getOrDefault(GEMINI,
                        ProviderHealth.notConfigured(GEMINI, "Hələ yoxlanılmayıb")));
    }

    /**
     * Runs shortly after startup and then every five minutes. Five minutes is the target for
     * "how long can a broken credential stay unnoticed" - short enough to catch it before most
     * business hours traffic, long enough to be a negligible number of API calls.
     */
    @Scheduled(initialDelay = 30_000, fixedDelay = 300_000)
    public void check() {
        record(checkElevenLabs());
        record(checkGemini());
    }

    /** Logs only on transitions, so a healthy system stays quiet and a break is loud. */
    private void record(ProviderHealth health) {
        ProviderHealth previous = state.put(health.name(), health);
        boolean changed = previous == null || previous.status() != health.status();
        if (!changed) {
            return;
        }
        if (health.status() == ProviderHealth.Status.DOWN) {
            log.error("PROVIDER DOWN - {}: {}. Calls depending on it will fail until this is fixed.",
                    health.name(), health.detail());
        } else {
            log.info("Provider {} is now {}: {}", health.name(), health.status(), health.detail());
        }
    }

    private ProviderHealth checkElevenLabs() {
        if (!StringUtils.hasText(elevenLabsApiKey)) {
            return ProviderHealth.notConfigured(ELEVENLABS,
                    "Açar konfiqurasiya olunmayıb (VOINT_ELEVENLABS_API_KEY)");
        }
        try {
            restClient.get()
                    .uri("https://api.elevenlabs.io/v1/user")
                    .header("xi-api-key", elevenLabsApiKey)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            return ProviderHealth.down(ELEVENLABS,
                    "Açar qəbul edilmir - yeni açar yaradıb Vapi-də yeniləmək lazımdır (" + e.getMessage() + ")");
        }

        // The key can be valid while the specific voice we speak with is gone; both break a call.
        if (StringUtils.hasText(elevenLabsVoiceId)) {
            try {
                restClient.get()
                        .uri("https://api.elevenlabs.io/v1/voices/" + elevenLabsVoiceId)
                        .header("xi-api-key", elevenLabsApiKey)
                        .retrieve()
                        .toBodilessEntity();
            } catch (Exception e) {
                return ProviderHealth.down(ELEVENLABS,
                        "Açar işləyir, amma səs " + elevenLabsVoiceId + " tapılmadı (" + e.getMessage() + ")");
            }
        }
        return ProviderHealth.ok(ELEVENLABS, "Açar və səs qüvvədədir");
    }

    private ProviderHealth checkGemini() {
        if (!geminiApiClient.isConfigured()) {
            return ProviderHealth.notConfigured(GEMINI, "Açar konfiqurasiya olunmayıb (GEMINI_API_KEY)");
        }
        try {
            // Cheapest call that still proves the key works end to end.
            float[] embedding = geminiApiClient.embedContent("saglamliq yoxlamasi");
            if (embedding == null) {
                return ProviderHealth.down(GEMINI, "Embedding cavabı boş qayıtdı - açar və ya kvota problemi");
            }
            return ProviderHealth.ok(GEMINI, "Açar qüvvədədir");
        } catch (Exception e) {
            return ProviderHealth.down(GEMINI, "Çağırış uğursuz oldu (" + e.getMessage() + ")");
        }
    }
}
