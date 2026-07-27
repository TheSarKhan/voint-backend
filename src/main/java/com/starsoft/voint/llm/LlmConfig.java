package com.starsoft.voint.llm;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;

/**
 * Picks the {@link LlmClient} implementation.
 *
 * <p>The decision is made per call, not at startup, because the Gemini key can now be set from the
 * admin panel: a bean chosen at boot would pin a freshly-configured platform to the mock until
 * someone restarted it, and would keep answering "mock cavab: ..." to real callers in the meantime.
 */
@Slf4j
@Configuration
public class LlmConfig {

    @Bean
    public LlmClient llmClient(GeminiApiClient geminiApiClient) {
        MockLlmClient mock = new MockLlmClient();
        GeminiLlmClient gemini = new GeminiLlmClient(geminiApiClient);
        return new SwitchingLlmClient(geminiApiClient, gemini, mock);
    }

    /** Delegates to Gemini whenever a key is configured, and to the mock only when none is. */
    record SwitchingLlmClient(GeminiApiClient geminiApiClient, LlmClient gemini, LlmClient mock)
            implements LlmClient {

        private LlmClient active() {
            if (geminiApiClient.isConfigured()) {
                return gemini;
            }
            log.warn("No Gemini API key configured - answering with MockLlmClient. Set it in the admin "
                    + "panel (Ayarlar) or via GEMINI_API_KEY.");
            return mock;
        }

        @Override
        public LlmResult complete(String systemPrompt, String userMessage) {
            return active().complete(systemPrompt, userMessage);
        }

        @Override
        public LlmResult completeStreaming(String systemPrompt, String userMessage,
                                           java.util.function.Consumer<String> onFragment) {
            return active().completeStreaming(systemPrompt, userMessage, onFragment);
        }
    }
}
