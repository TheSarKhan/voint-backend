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
            "Vapi konfiqurasiyasını bu paneldən yeniləmək üçün lazımdır.");

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
