package com.starsoft.voint.llm;

/** Result of an LLM completion. Token counts are 0 for the mock implementation. */
public record LlmResult(String content, int promptTokens, int completionTokens) {

    public int totalTokens() {
        return promptTokens + completionTokens;
    }
}
