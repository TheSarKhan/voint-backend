package com.starsoft.voint.rag;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.starsoft.voint.rag.dto.RagDocumentCreateRequest;
import com.starsoft.voint.rag.dto.RagDocumentResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/tenants/{id}/rag/documents")
@RequiredArgsConstructor
@Tag(name = "RAG", description = "Tenant knowledge base (pgvector-backed RAG documents)")
public class RagController {

    private final RagService ragService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a RAG document for the tenant (embedding computed in a later stage)")
    public RagDocumentResponse create(@PathVariable("id") UUID tenantId,
                                      @Valid @RequestBody RagDocumentCreateRequest request) {
        return RagDocumentResponse.from(ragService.create(tenantId, request));
    }

    @GetMapping
    @Operation(summary = "List all RAG documents of the tenant")
    public List<RagDocumentResponse> list(@PathVariable("id") UUID tenantId) {
        return ragService.list(tenantId).stream().map(RagDocumentResponse::from).toList();
    }

    @DeleteMapping("/{docId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a RAG document")
    public void delete(@PathVariable("id") UUID tenantId, @PathVariable UUID docId) {
        ragService.delete(tenantId, docId);
    }
}
