package com.starsoft.voint.rag.dto;

import jakarta.validation.constraints.NotBlank;

public record RagDocumentCreateRequest(
        @NotBlank String content,
        String category,
        String source
) {
}
