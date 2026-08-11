package com.starsoft.voint.rag.dto;

import jakarta.validation.constraints.NotBlank;

public record RagSimilarityCheckRequest(@NotBlank String content) {
}
