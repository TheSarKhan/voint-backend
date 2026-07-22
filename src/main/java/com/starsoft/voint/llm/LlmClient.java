package com.starsoft.voint.llm;

/**
 * Swappable LLM abstraction. Production implementation will call Gemini Flash;
 * the bootstrap stage uses {@link MockLlmClient}.
 */
public interface LlmClient {

    /**
     * @param systemPrompt built prompt (persona + boundaries + RAG context)
     * @param userMessage  latest user utterance
     * @return the assistant answer to be spoken by TTS
     */
    LlmResult complete(String systemPrompt, String userMessage);
}
