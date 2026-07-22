package com.starsoft.voint.llm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * Picks the {@link LlmClient} implementation: {@link GeminiLlmClient} when {@code GEMINI_API_KEY}
 * (bound to {@code voint.gemini.api-key}) is non-blank, otherwise {@link MockLlmClient} - this is
 * done explicitly here (rather than {@code @ConditionalOnProperty}, which cannot match "non-empty")
 * so the fallback behaviour is unambiguous and easy to follow.
 */
@Slf4j
@Configuration
public class LlmConfig {

    @Bean
    public LlmClient llmClient(GeminiApiClient geminiApiClient,
                                @Value("${voint.gemini.api-key:}") String geminiApiKey) {
        if (StringUtils.hasText(geminiApiKey)) {
            log.info("voint.gemini.api-key is set - using GeminiLlmClient for the voice webhook");
            return new GeminiLlmClient(geminiApiClient);
        }
        log.warn("voint.gemini.api-key is NOT set - falling back to MockLlmClient (echoes input, no real AI). "
                + "Set GEMINI_API_KEY to use real Gemini Flash.");
        return new MockLlmClient();
    }
}
