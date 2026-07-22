package com.starsoft.voint.voice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** One completion choice in the OpenAI-compatible response. */
public record ChatChoice(
        int index,
        ChatMessage message,
        @JsonProperty("finish_reason") String finishReason
) {
}
