package com.starsoft.voint.voice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Token usage block of the OpenAI-compatible response. Mock values at this stage. */
public record ChatUsage(
        @JsonProperty("prompt_tokens") int promptTokens,
        @JsonProperty("completion_tokens") int completionTokens,
        @JsonProperty("total_tokens") int totalTokens
) {
}
