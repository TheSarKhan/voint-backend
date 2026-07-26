package com.starsoft.voint.voice.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One SSE frame of an OpenAI-compatible streamed completion ({@code chat.completion.chunk}).
 *
 * Vapi's custom-LLM integration talks to us through an OpenAI client. When it sends
 * {@code "stream": true} it expects {@code text/event-stream} back, NOT a single JSON body —
 * a plain body leaves its parser with nothing to say, the agent stays silent and the call
 * dies with {@code silence-timed-out}.
 *
 * Frame sequence: one or more content deltas, then a final frame carrying
 * {@code finish_reason: "stop"} with an empty delta, then the literal {@code data: [DONE]}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatCompletionChunk(
        String id,
        String object,
        long created,
        String model,
        List<ChunkChoice> choices
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChunkChoice(
            int index,
            Delta delta,
            @JsonProperty("finish_reason") String finishReason
    ) {
    }

    /** Only the first frame carries {@code role}; the rest carry {@code content} alone. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Delta(String role, String content) {
    }

    public static ChatCompletionChunk content(String id, String model, String text, boolean first) {
        return new ChatCompletionChunk(id, "chat.completion.chunk", Instant.now().getEpochSecond(),
                model, List.of(new ChunkChoice(0, new Delta(first ? "assistant" : null, text), null)));
    }

    public static ChatCompletionChunk stop(String id, String model) {
        return new ChatCompletionChunk(id, "chat.completion.chunk", Instant.now().getEpochSecond(),
                model, List.of(new ChunkChoice(0, new Delta(null, null), "stop")));
    }
}
