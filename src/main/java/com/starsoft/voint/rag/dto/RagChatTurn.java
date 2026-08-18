package com.starsoft.voint.rag.dto;

import jakarta.validation.constraints.NotBlank;

/** One turn in the RAG-building interview chat. role: "user" (the tenant) or "assistant" (Gemini). */
public record RagChatTurn(@NotBlank String role, @NotBlank String content) {
}
