package com.starsoft.voint.health;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.google.auth.oauth2.GoogleCredentials;

import lombok.extern.slf4j.Slf4j;

/**
 * Asks a provider whether a given credential actually works.
 *
 * <p>Deliberately takes the credential as an argument rather than reading configuration, so the
 * exact same check serves two purposes: the scheduled monitor probes the credential currently in
 * use, and the settings screen probes a candidate one <em>before</em> saving it. A key that fails
 * here never reaches the database, which is what stops someone pasting a key ID instead of the
 * secret and only finding out when a customer hears silence.
 */
@Slf4j
@Component
public class ProviderProbe {

    private final RestClient restClient;

    public ProviderProbe() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /** Result of a single probe. {@code detail} is written for the operator, in Azerbaijani. */
    public record Result(boolean ok, String detail) {

        static Result ok(String detail) {
            return new Result(true, detail);
        }

        static Result fail(String detail) {
            return new Result(false, detail);
        }
    }

    /**
     * Checks the key, and separately that the voice still exists - a valid key pointing at a
     * deleted voice breaks a call just as completely, and sounds identical to the caller.
     */
    public Result elevenLabs(String apiKey, String voiceId) {
        if (!StringUtils.hasText(apiKey)) {
            return Result.fail("Açar boşdur");
        }
        if (!apiKey.startsWith("sk_")) {
            // Caught this exact mistake in practice: the API Keys list only lets you copy the key
            // ID, while the secret is shown once at creation and never again.
            return Result.fail("Bu açar deyil - ElevenLabs açarları \"sk_\" ilə başlayır. "
                    + "Siyahıdan kopyaladığın dəyər key ID-dir; açarın özü yalnız yaradılan anda görünür.");
        }
        try {
            restClient.get()
                    .uri("https://api.elevenlabs.io/v1/user")
                    .header("xi-api-key", apiKey)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            return Result.fail("ElevenLabs açarı qəbul etmir: " + shortMessage(e));
        }

        if (StringUtils.hasText(voiceId)) {
            try {
                restClient.get()
                        .uri("https://api.elevenlabs.io/v1/voices/" + voiceId)
                        .header("xi-api-key", apiKey)
                        .retrieve()
                        .toBodilessEntity();
            } catch (Exception e) {
                return Result.fail("Açar işləyir, amma \"" + voiceId + "\" səsi tapılmadı: " + shortMessage(e));
            }
            return Result.ok("Açar və səs qüvvədədir");
        }
        return Result.ok("Açar qüvvədədir (səs ID təyin olunmayıb)");
    }

    public Result gemini(String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            return Result.fail("Açar boşdur");
        }
        try {
            restClient.get()
                    .uri(b -> b.scheme("https").host("generativelanguage.googleapis.com")
                            .path("/v1beta/models").queryParam("key", apiKey).build())
                    .retrieve()
                    .toBodilessEntity();
            return Result.ok("Açar qüvvədədir");
        } catch (Exception e) {
            return Result.fail("Gemini açarı qəbul etmir: " + shortMessage(e));
        }
    }

    /**
     * Parses the service-account JSON and asks Google for a real OAuth access token with it -
     * a malformed JSON fails locally, a well-formed but revoked/wrong key fails against Google
     * itself, and only a token that actually comes back means the key really works. Doesn't
     * confirm Speech-to-Text access specifically (that needs a live streaming call, which the
     * custom-transcriber bridge itself is the real test of) - this is the same-tier check the
     * other probes here do: "is this credential alive," not "does every feature work."
     */
    public Result googleStt(String credentialsJson) {
        if (!StringUtils.hasText(credentialsJson)) {
            return Result.fail("JSON boşdur");
        }
        try {
            GoogleCredentials credentials = GoogleCredentials
                    .fromStream(new ByteArrayInputStream(credentialsJson.getBytes(StandardCharsets.UTF_8)))
                    .createScoped(List.of("https://www.googleapis.com/auth/cloud-platform"));
            credentials.refreshAccessToken();
            return Result.ok("Kredensial qüvvədədir");
        } catch (Exception e) {
            return Result.fail("Google kredensialı qəbul etmir: " + shortMessage(e));
        }
    }

    public Result telegram(String botToken) {
        if (!StringUtils.hasText(botToken)) {
            return Result.fail("Token boşdur");
        }
        try {
            restClient.get()
                    .uri("https://api.telegram.org/bot" + botToken + "/getMe")
                    .retrieve()
                    .toBodilessEntity();
            return Result.ok("Bot tokeni qüvvədədir");
        } catch (Exception e) {
            return Result.fail("Telegram tokeni qəbul etmir: " + shortMessage(e));
        }
    }

    public Result vapi(String privateKey) {
        if (!StringUtils.hasText(privateKey)) {
            return Result.fail("Açar boşdur");
        }
        try {
            restClient.get()
                    .uri("https://api.vapi.ai/assistant")
                    .header("Authorization", "Bearer " + privateKey)
                    .retrieve()
                    .toBodilessEntity();
            return Result.ok("Açar qüvvədədir");
        } catch (Exception e) {
            return Result.fail("Vapi açarı qəbul etmir: " + shortMessage(e));
        }
    }

    /** Provider errors carry whole response bodies; the operator only needs the first line. */
    private String shortMessage(Exception e) {
        String message = e.getMessage();
        if (message == null) {
            return e.getClass().getSimpleName();
        }
        return message.length() > 160 ? message.substring(0, 160) + "…" : message;
    }
}
