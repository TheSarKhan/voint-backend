package com.starsoft.voint.rag.dto;

/** Result of an embedding backfill pass - startup or admin-triggered. */
public record RagBackfillResponse(int total, int embedded, boolean geminiConfigured) {
}
