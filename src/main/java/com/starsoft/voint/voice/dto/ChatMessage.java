package com.starsoft.voint.voice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** OpenAI-compatible chat message: {"role": "user"|"assistant"|"system", "content": "..."}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatMessage(String role, String content) {
}
