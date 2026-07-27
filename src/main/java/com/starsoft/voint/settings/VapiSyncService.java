package com.starsoft.voint.settings;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Pushes credential changes into Vapi so there is exactly one place to change a key.
 *
 * <p>The problem this removes: Vapi holds its own copy of the ElevenLabs key and does the actual
 * speech synthesis, while this backend holds a copy only to monitor it. When the key was rotated,
 * both had to be updated by hand in unrelated places, and nothing in the product could tell you
 * whether either was right. Now saving the key in the admin panel updates Vapi too.
 *
 * <p>Failures are reported, never swallowed: a half-applied rotation - saved here, stale in Vapi -
 * is worse than a rejected save, because calls keep failing while the panel claims to be healthy.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VapiSyncService {

    private static final String VAPI_BASE = "https://api.vapi.ai";
    private static final String ELEVENLABS_PROVIDER = "11labs";

    private final PlatformSettingsService settings;

    /**
     * Uses the JDK HttpClient factory rather than SimpleClientHttpRequestFactory: the latter is
     * built on HttpURLConnection, which rejects PATCH outright ("Invalid HTTP method: PATCH") -
     * and PATCH is the only verb Vapi offers for updating a credential.
     */
    private RestClient client() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(15));
        return RestClient.builder().baseUrl(VAPI_BASE).requestFactory(factory).build();
    }

    /**
     * Writes the current ElevenLabs key into every {@code 11labs} credential Vapi holds.
     *
     * @return a human-readable summary of what was updated
     * @throws VapiSyncException if Vapi could not be reached or rejected the change
     */
    public String syncElevenLabsKey() {
        String vapiKey = settings.get(SettingKey.VAPI_PRIVATE_KEY);
        String elevenKey = settings.get(SettingKey.ELEVENLABS_API_KEY);
        if (!StringUtils.hasText(vapiKey)) {
            throw new VapiSyncException("Vapi private açarı təyin olunmayıb - Vapi yenilənə bilmədi");
        }
        if (!StringUtils.hasText(elevenKey)) {
            throw new VapiSyncException("ElevenLabs açarı boşdur");
        }

        RestClient rest = client();
        List<String> ids;
        try {
            JsonNode credentials = rest.get()
                    .uri("/credential")
                    .header("Authorization", "Bearer " + vapiKey)
                    .retrieve()
                    .body(JsonNode.class);
            ids = elevenLabsCredentialIds(credentials);
        } catch (Exception e) {
            throw new VapiSyncException("Vapi credential siyahısı alınmadı: " + e.getMessage(), e);
        }

        if (ids.isEmpty()) {
            throw new VapiSyncException("Vapi-də ElevenLabs credential tapılmadı - əvvəlcə Vapi-də yaradılmalıdır");
        }

        for (String id : ids) {
            try {
                rest.patch()
                        .uri("/credential/{id}", id)
                        .header("Authorization", "Bearer " + vapiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("provider", ELEVENLABS_PROVIDER, "apiKey", elevenKey))
                        .retrieve()
                        .toBodilessEntity();
            } catch (Exception e) {
                throw new VapiSyncException("Vapi credential " + id + " yenilənmədi: " + e.getMessage(), e);
            }
        }
        log.info("Pushed the ElevenLabs key to {} Vapi credential(s)", ids.size());
        return ids.size() + " Vapi credential yeniləndi";
    }

    /** Points every assistant that already speaks with ElevenLabs at the configured voice. */
    public String syncVoiceId() {
        String vapiKey = settings.get(SettingKey.VAPI_PRIVATE_KEY);
        String voiceId = settings.get(SettingKey.ELEVENLABS_VOICE_ID);
        if (!StringUtils.hasText(vapiKey) || !StringUtils.hasText(voiceId)) {
            throw new VapiSyncException("Vapi açarı və ya səs ID boşdur");
        }

        RestClient rest = client();
        JsonNode assistants;
        try {
            assistants = rest.get()
                    .uri("/assistant")
                    .header("Authorization", "Bearer " + vapiKey)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception e) {
            throw new VapiSyncException("Vapi assistant siyahısı alınmadı: " + e.getMessage(), e);
        }

        int updated = 0;
        for (JsonNode assistant : arrayOf(assistants)) {
            JsonNode voice = assistant.path("voice");
            if (!ELEVENLABS_PROVIDER.equals(voice.path("provider").asText(null))) {
                continue;
            }
            if (voiceId.equals(voice.path("voiceId").asText(null))) {
                continue;
            }
            String id = assistant.path("id").asText(null);
            try {
                // Merge rather than replace: stability, style and similarityBoost were tuned by
                // ear for Azerbaijani, and sending a bare voice block would silently reset them.
                var body = new java.util.HashMap<String, Object>();
                voice.fields().forEachRemaining(e -> body.put(e.getKey(), e.getValue().isValueNode()
                        ? e.getValue().asText() : e.getValue()));
                body.put("voiceId", voiceId);
                rest.patch()
                        .uri("/assistant/{id}", id)
                        .header("Authorization", "Bearer " + vapiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("voice", body))
                        .retrieve()
                        .toBodilessEntity();
                updated++;
            } catch (Exception e) {
                throw new VapiSyncException("Vapi assistant " + id + " yenilənmədi: " + e.getMessage(), e);
            }
        }
        return updated == 0 ? "Assistant-lar artıq bu səsi işlədir" : updated + " assistant yeniləndi";
    }

    private List<String> elevenLabsCredentialIds(JsonNode credentials) {
        return arrayOf(credentials).stream()
                .filter(c -> ELEVENLABS_PROVIDER.equals(c.path("provider").asText(null)))
                .map(c -> c.path("id").asText(null))
                .filter(StringUtils::hasText)
                .toList();
    }

    /** Vapi returns a bare array on these endpoints, but tolerate a {results:[...]} envelope. */
    private List<JsonNode> arrayOf(JsonNode node) {
        if (node == null) {
            return List.of();
        }
        JsonNode array = node.isArray() ? node : node.path("results");
        if (!array.isArray()) {
            return List.of();
        }
        return java.util.stream.StreamSupport.stream(array.spliterator(), false).toList();
    }

    public static class VapiSyncException extends RuntimeException {
        public VapiSyncException(String message) {
            super(message);
        }

        public VapiSyncException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
