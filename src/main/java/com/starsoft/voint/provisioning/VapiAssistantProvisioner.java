package com.starsoft.voint.provisioning;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.starsoft.voint.settings.PlatformSettingsService;
import com.starsoft.voint.settings.SettingKey;
import com.starsoft.voint.tenant.Tenant;

import lombok.extern.slf4j.Slf4j;

/**
 * Creates and maintains one Vapi assistant per tenant.
 *
 * <p>Twenty-two settings used to be typed into Vapi's dashboard by hand for every new business.
 * Most of them are identical everywhere; the few that are not include {@code metadata.tenant_id},
 * and getting that one wrong does not produce an error - it produces a business answering its
 * callers out of another business's knowledge base, fluently and confidently. This class exists so
 * that mistake cannot be made.
 *
 * <p>It is also the single place the voice settings live. They were tuned by ear against real
 * Azerbaijani calls (stability at 0 so questions keep their intonation, speaker boost on to stop
 * the voice drifting mid-call); with per-assistant hand configuration, improving them later would
 * mean editing every assistant by hand and getting one of them subtly wrong.
 */
@Slf4j
@Service
public class VapiAssistantProvisioner {

    private static final String VAPI_BASE = "https://api.vapi.ai";

    /**
     * Place names and everyday words every Azerbaijani business needs the transcriber to know.
     * Shared deliberately - unlike industry terms, these help everyone and hurt nobody.
     */
    private static final List<String> SHARED_VOCABULARY = List.of(
            "Bakı", "Sumqayıt", "Gəncə", "Xırdalan", "Abşeron", "Mingəçevir", "Şirvan",
            "Naxçıvan", "Lənkəran", "Şəki", "Quba", "Qusar", "Xaçmaz", "Qəbələ", "Zaqatala",
            "Salyan", "Masallı", "Bərdə", "Yevlax", "Tərtər", "Ağdam", "Füzuli", "Şuşa",
            "Xankəndi", "Qarabağ", "Daşkəsən", "Mərdəkan", "Buzovna", "Sabunçu", "Binəqədi",
            "Nizami", "Xətai", "Yasamal", "Nərimanov",
            "manat", "qəpik", "salam", "sağ olun");

    /**
     * Not "words the model doesn't know" - the opposite: these are the shortest, most acoustically
     * ambiguous words in the language, and a call turning on the wrong one of them (a cancellation
     * heard as a confirmation) is the worst possible transcription error. Boosting a short, fixed
     * list of exactly these high-stakes tokens is a different move from padding the vocabulary with
     * ordinary words - it exists to break ties in the model's favour on the words where a tie going
     * the wrong way is expensive, not to teach it words it doesn't already know.
     */
    private static final List<String> CRITICAL_CONFIRMATION_WORDS = List.of(
            "bəli", "xeyr", "hə", "yox", "yoxdur", "rədd", "rədd edirəm", "ləğv et",
            "ləğv edirəm", "təsdiq", "təsdiqləyirəm", "tamam", "oldu", "düzdür", "səhvdir",
            "istəmirəm", "istəyirəm");

    /**
     * Said at the start of every call, before the tenant's own greeting - a business owner writing
     * their greeting cannot be trusted to remember a compliance disclosure, and would have no
     * reason to word it consistently even if they did. Vapi speaks {@code firstMessage} directly
     * (it never goes through the custom-LLM webhook), so this is the one place a disclosure here
     * is guaranteed to be said verbatim, not paraphrased or dropped by the LLM.
     */
    private static final String RECORDING_DISCLOSURE =
            "Bildirmək istəyirəm ki, bu zəng keyfiyyət məqsədilə qeydə alınır və sizinlə süni intellekt "
                    + "agenti danışır.";

    private final PlatformSettingsService settings;
    private final String publicBaseUrl;
    private final String webhookSecret;

    public VapiAssistantProvisioner(
            PlatformSettingsService settings,
            @Value("${voint.public-base-url:https://voint.sarkhan.az}") String publicBaseUrl,
            @Value("${voint.vapi.webhook-secret:}") String webhookSecret) {
        this.settings = settings;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
        this.webhookSecret = webhookSecret;
    }

    /**
     * Creates the tenant's assistant, or updates it if one already exists.
     *
     * @return the Vapi assistant id
     * @throws ProvisioningException if Vapi is unreachable or rejects the assistant
     */
    public String provision(Tenant tenant) {
        String vapiKey = settings.get(SettingKey.VAPI_PRIVATE_KEY);
        if (!StringUtils.hasText(vapiKey)) {
            throw new ProvisioningException("Vapi private açarı təyin olunmayıb - Ayarlar bölməsindən əlavə et");
        }

        Map<String, Object> body = buildAssistant(tenant);
        RestClient rest = client();

        try {
            if (StringUtils.hasText(tenant.getVapiAssistantId())) {
                rest.patch()
                        .uri("/assistant/{id}", tenant.getVapiAssistantId())
                        .header("Authorization", "Bearer " + vapiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .toBodilessEntity();
                log.info("Updated Vapi assistant {} for tenant {}", tenant.getVapiAssistantId(), tenant.getId());
                return tenant.getVapiAssistantId();
            }

            JsonNode created = rest.post()
                    .uri("/assistant")
                    .header("Authorization", "Bearer " + vapiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            String id = created != null ? created.path("id").asText(null) : null;
            if (!StringUtils.hasText(id)) {
                throw new ProvisioningException("Vapi assistant yaratdı, amma id qaytarmadı");
            }
            log.info("Created Vapi assistant {} for tenant {}", id, tenant.getId());
            return id;
        } catch (ProvisioningException e) {
            throw e;
        } catch (Exception e) {
            throw new ProvisioningException("Vapi assistant qurula bilmədi: " + e.getMessage(), e);
        }
    }

    /** Everything Vapi needs to answer a call on this tenant's behalf. */
    private Map<String, Object> buildAssistant(Tenant tenant) {
        Map<String, Object> assistant = new LinkedHashMap<>();
        assistant.put("name", "Voint - " + tenant.getName());

        // The whole point. Without this the webhook cannot tell whose call it is and falls back to
        // the bootstrap tenant, i.e. answers out of the wrong company's knowledge base.
        assistant.put("metadata", Map.of("tenant_id", tenant.getId().toString()));

        assistant.put("firstMessage", StringUtils.hasText(tenant.getGreetingText())
                ? RECORDING_DISCLOSURE + " " + tenant.getGreetingText()
                : RECORDING_DISCLOSURE);
        assistant.put("endCallMessage", "Zəng etdiyiniz üçün təşəkkür edirik, gözəl gün arzulayırıq!");

        assistant.put("model", model());
        assistant.put("voice", voice());
        assistant.put("transcriber", transcriber(tenant));
        assistant.put("server", server());
        return assistant;
    }

    private Map<String, Object> model() {
        Map<String, Object> headers = new LinkedHashMap<>();
        if (StringUtils.hasText(webhookSecret)) {
            headers.put("x-vapi-secret", webhookSecret);
        }
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("provider", "custom-llm");
        model.put("model", "voint-agent");
        // Vapi treats this as an OpenAI base URL and appends /chat/completions itself.
        model.put("url", publicBaseUrl + "/api/v1/voice");
        model.put("headers", headers);
        return model;
    }

    /**
     * Tuned by ear against real Azerbaijani calls, not copied from documentation:
     * stability 0 keeps question intonation (higher values flattened it into statements),
     * speaker boost stops the voice drifting into a different-sounding person mid-call.
     */
    private Map<String, Object> voice() {
        Map<String, Object> voice = new LinkedHashMap<>();
        voice.put("provider", "11labs");
        voice.put("voiceId", settings.get(SettingKey.ELEVENLABS_VOICE_ID));
        // v3 is the only ElevenLabs model that speaks Azerbaijani at all.
        voice.put("model", "eleven_v3");
        voice.put("language", "az");
        voice.put("stability", 0);
        voice.put("style", 0.6);
        voice.put("similarityBoost", 0.95);
        voice.put("useSpeakerBoost", true);
        voice.put("optimizeStreamingLatency", 0);
        // ElevenLabs allows 0.7-1.2 (1.0 = normal pace); the default read as sluggish on a phone
        // call, where a pause reads as "did it hang up" rather than "thinking".
        voice.put("speed", 1.15);
        return voice;
    }

    private Map<String, Object> transcriber(Tenant tenant) {
        if ("google".equals(tenant.getSttProvider())) {
            return googleTranscriber();
        }
        return sonioxTranscriber(tenant);
    }

    /**
     * A/B-test branch (see {@link Tenant#getSttProvider}). Vapi's default, pooled Google
     * integration - no credential of ours involved, unlike every other provider block in this
     * class. Azerbaijani has no dedicated mode in Vapi's Google transcriber, only "Multilingual"
     * auto-detection - this is a real handicap next to Soniox's dedicated "az" mode, which is
     * exactly why this needs a live comparison rather than a guess.
     */
    private Map<String, Object> googleTranscriber() {
        Map<String, Object> transcriber = new LinkedHashMap<>();
        transcriber.put("provider", "google");
        transcriber.put("model", "gemini-2.0-flash");
        transcriber.put("language", "Multilingual");
        return transcriber;
    }

    private Map<String, Object> sonioxTranscriber(Tenant tenant) {
        Map<String, Object> transcriber = new LinkedHashMap<>();
        transcriber.put("provider", "soniox");
        transcriber.put("model", "stt-rt-v5");
        transcriber.put("language", "az");

        List<Map<String, String>> context = new ArrayList<>();
        if (StringUtils.hasText(tenant.getSttDomain())) {
            context.add(Map.of("key", "domain", "value", tenant.getSttDomain()));
        }
        if (StringUtils.hasText(tenant.getSttTopic())) {
            context.add(Map.of("key", "topic", "value", tenant.getSttTopic()));
        }
        if (!context.isEmpty()) {
            transcriber.put("contextGeneral", context);
        }

        transcriber.put("customVocabulary", vocabularyFor(tenant));
        // Azerbaijani speakers pause mid-sentence more than the default assumes; cutting them off
        // produced half-questions that the agent then answered wrongly.
        transcriber.put("maxEndpointDelayMs", 1200);
        return transcriber;
    }

    /** Shared Azerbaijani terms plus whatever this particular business deals in. */
    private List<String> vocabularyFor(Tenant tenant) {
        Set<String> words = new LinkedHashSet<>(SHARED_VOCABULARY);
        words.addAll(CRITICAL_CONFIRMATION_WORDS);
        if (StringUtils.hasText(tenant.getSttVocabulary())) {
            Arrays.stream(tenant.getSttVocabulary().split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .forEach(words::add);
        }
        return List.copyOf(words);
    }

    private Map<String, Object> server() {
        Map<String, Object> server = new LinkedHashMap<>();
        server.put("url", publicBaseUrl + "/api/v1/voice/events");
        if (StringUtils.hasText(webhookSecret)) {
            server.put("headers", Map.of("x-vapi-secret", webhookSecret));
        }
        return server;
    }

    private RestClient client() {
        // JDK HttpClient rather than the Simple factory: the latter cannot issue PATCH, which is
        // how Vapi updates an existing assistant.
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(20));
        return RestClient.builder().baseUrl(VAPI_BASE).requestFactory(factory).build();
    }

    public static class ProvisioningException extends RuntimeException {
        public ProvisioningException(String message) {
            super(message);
        }

        public ProvisioningException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
