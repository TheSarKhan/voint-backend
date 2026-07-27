package com.starsoft.voint.llm;

import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

/**
 * Real Gemini Flash implementation of {@link LlmClient}. Instantiated by {@link LlmConfig}
 * (not a {@code @Component}) when {@code GEMINI_API_KEY} is set; otherwise {@link MockLlmClient}
 * is used instead.
 *
 * A voice call must never hard-fail into a 500: any Gemini failure (network, 4xx/5xx, unexpected
 * response shape) is caught and turned into a safe Azerbaijani "please hold / let me hand you off"
 * style fallback answer.
 */
@Slf4j
public class GeminiLlmClient implements LlmClient {

    private static final String FALLBACK_MESSAGE =
            "Üzr istəyirəm, hazırda sualınızı emal edərkən texniki problem yarandı. "
                    + "Bir azdan yenidən cəhd edin, ya da sizi əməkdaşımızla əlaqələndirim.";

    private final GeminiApiClient geminiApiClient;

    public GeminiLlmClient(GeminiApiClient geminiApiClient) {
        this.geminiApiClient = geminiApiClient;
    }

    @Override
    public LlmResult complete(String systemPrompt, String userMessage) {
        try {
            GeminiApiClient.GenerationResult result = geminiApiClient.generateContent(systemPrompt, userMessage);
            return new LlmResult(result.text(), result.promptTokens(), result.completionTokens());
        } catch (Exception e) {
            log.error("Gemini LLM call failed - returning safe fallback response instead of failing the call", e);
            return new LlmResult(FALLBACK_MESSAGE, 0, 0);
        }
    }

    /**
     * Deliberately lets failures propagate: the caller is streaming to a live phone line and is
     * the only one that knows whether any audio has already been committed, so it - not this
     * class - decides whether a fallback sentence can still be spoken.
     */
    @Override
    public LlmResult completeStreaming(String systemPrompt, String userMessage, Consumer<String> onFragment) {
        GeminiApiClient.GenerationResult result =
                geminiApiClient.generateContentStream(systemPrompt, userMessage, onFragment);
        return new LlmResult(result.text(), result.promptTokens(), result.completionTokens());
    }
}
