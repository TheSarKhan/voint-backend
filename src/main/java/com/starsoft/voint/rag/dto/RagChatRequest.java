package com.starsoft.voint.rag.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

/** Full conversation so far - stateless on the server, the panel resends it every turn. */
public record RagChatRequest(@NotEmpty List<RagChatTurn> history) {
}
