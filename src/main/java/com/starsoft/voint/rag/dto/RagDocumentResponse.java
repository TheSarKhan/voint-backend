package com.starsoft.voint.rag.dto;

import java.time.Instant;
import java.util.UUID;

import com.starsoft.voint.rag.RagDocument;

public record RagDocumentResponse(
        UUID id,
        UUID tenantId,
        String content,
        String category,
        String source,
        Instant createdAt
) {
    public static RagDocumentResponse from(RagDocument d) {
        return new RagDocumentResponse(d.getId(), d.getTenantId(), d.getContent(),
                d.getCategory(), d.getSource(), d.getCreatedAt());
    }
}
