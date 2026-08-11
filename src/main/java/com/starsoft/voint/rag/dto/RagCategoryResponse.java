package com.starsoft.voint.rag.dto;

import java.util.UUID;

import com.starsoft.voint.rag.RagCategory;

public record RagCategoryResponse(UUID id, String name) {
    public static RagCategoryResponse from(RagCategory c) {
        return new RagCategoryResponse(c.getId(), c.getName());
    }
}
