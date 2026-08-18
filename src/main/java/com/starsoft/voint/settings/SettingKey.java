package com.starsoft.voint.settings;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Every credential the platform can hold, and how it is presented in the admin panel.
 *
 * <p>{@code secret} marks values that must never be sent back to a browser: the panel only ever
 * receives a masked hint (last four characters) so an operator can tell which key is loaded
 * without the key itself travelling anywhere it does not need to go.
 */
@Getter
@RequiredArgsConstructor
public enum SettingKey {

    ELEVENLABS_API_KEY("elevenlabs.api-key", "ElevenLabs API açarı", true,
            "Səsi yaradır. Dəyişdirildikdə Vapi-yə avtomatik ötürülür."),

    ELEVENLABS_VOICE_ID("elevenlabs.voice-id", "ElevenLabs səs ID", false,
            "Agentin danışdığı səs. Dəyişdirildikdə Vapi assistant-ına ötürülür."),

    GEMINI_API_KEY("gemini.api-key", "Gemini API açarı", true,
            "Cavabları yazan model və RAG axtarışı üçün embedding."),

    VAPI_PRIVATE_KEY("vapi.private-key", "Vapi private açarı", true,
            "Vapi konfiqurasiyasını bu paneldən yeniləmək üçün lazımdır."),

    /**
     * The real Cloud Speech-to-Text product (dedicated az-AZ mode) - distinct from Vapi's own
     * "google" transcriber option, which turned out to run on Gemini and has no dedicated
     * Azerbaijani mode. Used only by our custom-transcriber WebSocket bridge, never sent to Vapi.
     */
    GOOGLE_STT_CREDENTIALS_JSON("google.stt-credentials-json", "Google STT service account (JSON)", true,
            "Google Cloud service account-ın tam JSON məzmunu - custom transcriber körpüsü üçün."),

    TELEGRAM_BOT_TOKEN("telegram.bot-token", "Telegram bot tokeni", true,
            "Zəng nəticələrini müəssisələrə Telegram ilə göndərən bot. Yadda saxlanılanda "
                    + "webhook avtomatik qeydiyyatdan keçir."),

    /**
     * Never typed by an operator - generated the first time a bot token is saved, so Telegram's
     * webhook calls can be told apart from anyone who guesses the URL. Still a SettingKey (not a
     * bare field) so it goes through the same encrypted storage as every other credential here.
     */
    TELEGRAM_WEBHOOK_SECRET("telegram.webhook-secret", "Telegram webhook sirri", true,
            "Avtomatik yaradılır - əl ilə dəyişmə."),

    /**
     * Base domain the tenant panels live under: "sarkhan.az" serves ces.sarkhan.az.
     *
     * <p>A setting rather than a constant because it WILL change - the platform is running on a
     * borrowed domain until voint.az is registered, and moving should be one field in this panel,
     * not a redeploy plus a hunt for hardcoded suffixes across two frontends.
     */
    PANEL_DOMAIN("panel.domain", "Panel domeni", false,
            "Müəssisə panellərinin ünvanı: ces.<domen>. Domen dəyişəndə yalnız buranı dəyiş."),

    /*
     * SMTP. Beşi də təyin olunmasa e-poçt göndərilmir və sistem şifrəni ekranda göstərməyə
     * qayıdır — yarımçıq konfiqurasiya ilə "göndərildi" deyib heç nə göndərməmək ən pisidir.
     */
    SMTP_HOST("smtp.host", "SMTP server", false,
            "Məsələn smtp.resend.com. Beş SMTP sahəsi dolmasa e-poçt göndərilmir."),

    SMTP_PORT("smtp.port", "SMTP port", false, "Adətən 587 (STARTTLS)."),

    SMTP_USERNAME("smtp.username", "SMTP istifadəçi", false, "Provayderin verdiyi istifadəçi adı."),

    SMTP_PASSWORD("smtp.password", "SMTP şifrə", true, "Provayderin verdiyi şifrə və ya API açarı."),

    SMTP_FROM("smtp.from", "Göndərən ünvan", false,
            "Məsələn Voint <panel@voint.az>. Domen SPF/DKIM ilə doğrulanmalıdır, yoxsa spama düşür.");

    private final String key;
    private final String label;
    private final boolean secret;
    private final String description;

    public static SettingKey fromKey(String key) {
        for (SettingKey k : values()) {
            if (k.key.equals(key)) {
                return k;
            }
        }
        throw new IllegalArgumentException("Unknown setting key: " + key);
    }
}
