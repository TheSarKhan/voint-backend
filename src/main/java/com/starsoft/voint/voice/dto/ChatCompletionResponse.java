package com.starsoft.voint.voice.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * OpenAI-compatible chat completion response - the shape Vapi expects back
 * from a custom-LLM endpoint (non-streaming).
 */
public record ChatCompletionResponse(
        String id,
        String object,
        long created,
        String model,
        List<ChatChoice> choices,
        ChatUsage usage
) {
    public static ChatCompletionResponse assistantMessage(String model, String content) {
        return new ChatCompletionResponse(
                "chatcmpl-" + UUID.randomUUID(),
                "chat.completion",
                Instant.now().getEpochSecond(),
                model != null ? model : "voint-mock",
                List.of(new ChatChoice(0, new ChatMessage("assistant", content), "stop")),
                new ChatUsage(0, 0, 0));
    }
}
