package com.starsoft.voint.rag.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;

public record RagBulkDeleteRequest(@NotEmpty List<UUID> ids) {
}
