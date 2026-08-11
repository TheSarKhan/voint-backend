package com.starsoft.voint.rag.dto;

import jakarta.validation.constraints.NotBlank;

public record RagDocumentUpdateRequest(
        @NotBlank String content,
        String category
) {
}
