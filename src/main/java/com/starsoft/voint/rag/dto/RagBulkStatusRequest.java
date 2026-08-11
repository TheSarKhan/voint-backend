package com.starsoft.voint.rag.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;

public record RagBulkStatusRequest(@NotEmpty List<UUID> ids, boolean active) {
}
