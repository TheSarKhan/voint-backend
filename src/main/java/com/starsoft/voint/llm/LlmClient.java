package com.starsoft.voint.llm;

import java.util.function.Consumer;

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

    /**
     * Same completion, but fragments are handed to {@code onFragment} as they are produced.
     *
     * <p>Unlike {@link #complete}, this does NOT swallow failures into a fallback answer: by the
     * time something goes wrong the caller may already have written part of the answer to the
     * wire, so only the caller knows whether a fallback is still possible.
     *
     * @param onFragment receives raw text fragments in order; may be called many times
     * @return the assembled answer and its token usage
     */
    LlmResult completeStreaming(String systemPrompt, String userMessage, Consumer<String> onFragment);
}
