package com.starsoft.voint.llm;

import lombok.extern.slf4j.Slf4j;

/**
 * Fallback mock used when {@code GEMINI_API_KEY} is not configured (e.g. CI, offline dev):
 * echoes the user message back as "mock cavab: {text}". Selected by {@link LlmConfig};
 * not a {@code @Component} itself to avoid a duplicate {@link LlmClient} bean alongside
 * {@link GeminiLlmClient}.
 */
@Slf4j
public class MockLlmClient implements LlmClient {

    @Override
    public LlmResult complete(String systemPrompt, String userMessage) {
        log.debug("MockLlmClient.complete - systemPrompt length={}, userMessage='{}'",
                systemPrompt != null ? systemPrompt.length() : 0, userMessage);
        return new LlmResult("mock cavab: " + userMessage, 0, 0);
    }
}
