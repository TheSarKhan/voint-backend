package com.starsoft.voint.settings;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolves platform credentials, preferring what an operator set in the admin panel and falling
 * back to what was configured on the server.
 *
 * <p>The fallback is deliberate and load-bearing: it means turning this feature on cannot break a
 * running system, and an empty database still answers phone calls exactly as before. The panel
 * takes over a credential only once someone actually saves one.
 *
 * <p>Values are cached in memory because the voice path reads them on every turn; the cache is
 * cleared on write, so a saved key takes effect on the next call with no restart.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformSettingsService {

    private final PlatformSettingRepository repository;
    private final SecretCipher cipher;

    private final Map<String, String> cache = new ConcurrentHashMap<>();
    /** Marks keys known to be absent from the DB, so a miss does not re-query on every turn. */
    private final Map<String, Boolean> knownAbsent = new ConcurrentHashMap<>();

    @Value("${voint.elevenlabs.api-key:}")
    private String configuredElevenLabsKey;

    @Value("${voint.elevenlabs.voice-id:}")
    private String configuredElevenLabsVoiceId;

    @Value("${voint.gemini.api-key:}")
    private String configuredGeminiKey;

    @Value("${voint.vapi.private-key:}")
    private String configuredVapiPrivateKey;

    @Value("${voint.telegram.bot-token:}")
    private String configuredTelegramBotToken;

    @Value("${voint.google.stt-credentials-json:}")
    private String configuredGoogleSttCredentialsJson;

    @Value("${voint.panel.domain:sarkhan.az}")
    private String configuredPanelDomain;

    @Value("${voint.smtp.host:}")
    private String configuredSmtpHost;

    @Value("${voint.smtp.port:587}")
    private String configuredSmtpPort;

    @Value("${voint.smtp.username:}")
    private String configuredSmtpUsername;

    @Value("${voint.smtp.password:}")
    private String configuredSmtpPassword;

    @Value("${voint.smtp.from:}")
    private String configuredSmtpFrom;

    /** The effective value: database first, then server configuration, else empty string. */
    public String get(SettingKey key) {
        String stored = fromDatabase(key);
        if (StringUtils.hasText(stored)) {
            return stored;
        }
        return configured(key);
    }

    public boolean isSetInDatabase(SettingKey key) {
        return StringUtils.hasText(fromDatabase(key));
    }

    @Transactional
    public void set(SettingKey key, String value, String updatedBy) {
        PlatformSetting setting = repository.findById(key.getKey()).orElseGet(() -> {
            PlatformSetting fresh = new PlatformSetting();
            fresh.setKey(key.getKey());
            return fresh;
        });
        setting.setValueEnc(cipher.encrypt(value));
        setting.setUpdatedAt(Instant.now());
        setting.setUpdatedBy(updatedBy);
        repository.save(setting);

        cache.put(key.getKey(), value);
        knownAbsent.remove(key.getKey());
        log.info("Platform setting '{}' updated by {}", key.getKey(), updatedBy);
    }

    /** Falls the credential back to whatever the server was configured with. */
    @Transactional
    public void clear(SettingKey key, String updatedBy) {
        repository.deleteById(key.getKey());
        cache.remove(key.getKey());
        knownAbsent.put(key.getKey(), Boolean.TRUE);
        log.info("Platform setting '{}' cleared by {} - falling back to server configuration",
                key.getKey(), updatedBy);
    }

    /** When each key was last changed and by whom; absent when it has never been set here. */
    @Transactional(readOnly = true)
    public Map<SettingKey, PlatformSetting> metadata() {
        Map<SettingKey, PlatformSetting> out = new EnumMap<>(SettingKey.class);
        for (SettingKey key : SettingKey.values()) {
            repository.findById(key.getKey()).ifPresent(s -> out.put(key, s));
        }
        return out;
    }

    private String fromDatabase(SettingKey key) {
        String cached = cache.get(key.getKey());
        if (cached != null) {
            return cached;
        }
        if (Boolean.TRUE.equals(knownAbsent.get(key.getKey()))) {
            return null;
        }
        Optional<PlatformSetting> row = repository.findById(key.getKey());
        if (row.isEmpty()) {
            knownAbsent.put(key.getKey(), Boolean.TRUE);
            return null;
        }
        String plain = cipher.decrypt(row.get().getValueEnc());
        if (plain == null) {
            // Unreadable (master key changed) - treat as unset so the server config still works.
            return null;
        }
        cache.put(key.getKey(), plain);
        return plain;
    }

    private String configured(SettingKey key) {
        return switch (key) {
            case ELEVENLABS_API_KEY -> nullSafe(configuredElevenLabsKey);
            case ELEVENLABS_VOICE_ID -> nullSafe(configuredElevenLabsVoiceId);
            case GEMINI_API_KEY -> nullSafe(configuredGeminiKey);
            case VAPI_PRIVATE_KEY -> nullSafe(configuredVapiPrivateKey);
            case TELEGRAM_BOT_TOKEN -> nullSafe(configuredTelegramBotToken);
            case GOOGLE_STT_CREDENTIALS_JSON -> nullSafe(configuredGoogleSttCredentialsJson);
            // Never comes from server config - see the field's own javadoc.
            case TELEGRAM_WEBHOOK_SECRET -> "";
            case PANEL_DOMAIN -> nullSafe(configuredPanelDomain);
            case SMTP_HOST -> nullSafe(configuredSmtpHost);
            case SMTP_PORT -> nullSafe(configuredSmtpPort);
            case SMTP_USERNAME -> nullSafe(configuredSmtpUsername);
            case SMTP_PASSWORD -> nullSafe(configuredSmtpPassword);
            case SMTP_FROM -> nullSafe(configuredSmtpFrom);
        };
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }
}
