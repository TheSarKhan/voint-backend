package com.starsoft.voint.rag;

import com.starsoft.voint.rbac.Permission;
import com.starsoft.voint.rbac.PublicEndpoint;
import com.starsoft.voint.rbac.RequirePermission;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.starsoft.voint.auth.TenantAccessGuard;
import com.starsoft.voint.rag.dto.RagBulkDeleteRequest;
import com.starsoft.voint.rag.dto.RagBulkStatusRequest;
import com.starsoft.voint.rag.dto.RagDocumentCreateRequest;
import com.starsoft.voint.rag.dto.RagDocumentResponse;
import com.starsoft.voint.rag.dto.RagDocumentStatusRequest;
import com.starsoft.voint.rag.dto.RagDocumentUpdateRequest;
import com.starsoft.voint.rag.dto.RagSimilarityCheckRequest;

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
    private final TenantAccessGuard tenantAccessGuard;

    @RequirePermission(resource = Permission.Resource.RAG, action = Permission.Action.CREATE)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a RAG document for the tenant (embedding computed in a later stage)")
    public RagDocumentResponse create(@PathVariable("id") UUID tenantId,
                                      @Valid @RequestBody RagDocumentCreateRequest request) {
        tenantAccessGuard.requireAccess(tenantId);
        return RagDocumentResponse.from(ragService.create(tenantId, request));
    }

    @RequirePermission(resource = Permission.Resource.RAG, action = Permission.Action.READ)
    @GetMapping
    @Operation(summary = "List all RAG documents of the tenant")
    public List<RagDocumentResponse> list(@PathVariable("id") UUID tenantId) {
        tenantAccessGuard.requireAccess(tenantId);
        return ragService.list(tenantId).stream().map(RagDocumentResponse::from).toList();
    }

    /** Extracts text out of a file the tenant already has (.txt/.docx/.pdf) instead of retyping it. */
    @RequirePermission(resource = Permission.Resource.RAG, action = Permission.Action.CREATE)
    @PostMapping("/upload")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a RAG document by extracting text from an uploaded .txt/.docx/.pdf file")
    public RagDocumentResponse upload(@PathVariable("id") UUID tenantId,
                                      @RequestParam("file") MultipartFile file,
                                      @RequestParam(required = false) String category) {
        tenantAccessGuard.requireAccess(tenantId);
        String text = RagFileExtractor.extract(file);
        String source = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload";
        RagDocumentCreateRequest request = new RagDocumentCreateRequest(text, category, source);
        return RagDocumentResponse.from(ragService.create(tenantId, request));
    }

    @RequirePermission(resource = Permission.Resource.RAG, action = Permission.Action.UPDATE)
    @PutMapping("/{docId}")
    @Operation(summary = "Edit a RAG document's content or category (re-embedded)")
    public RagDocumentResponse update(@PathVariable("id") UUID tenantId, @PathVariable UUID docId,
                                      @Valid @RequestBody RagDocumentUpdateRequest request) {
        tenantAccessGuard.requireAccess(tenantId);
        return RagDocumentResponse.from(ragService.update(tenantId, docId, request));
    }

    @RequirePermission(resource = Permission.Resource.RAG, action = Permission.Action.UPDATE)
    @PutMapping("/{docId}/status")
    @Operation(summary = "Pause or resume a document - paused ones are excluded from what the agent knows")
    public RagDocumentResponse setStatus(@PathVariable("id") UUID tenantId, @PathVariable UUID docId,
                                         @RequestBody RagDocumentStatusRequest request) {
        tenantAccessGuard.requireAccess(tenantId);
        return RagDocumentResponse.from(ragService.setActive(tenantId, docId, request.active()));
    }

    @RequirePermission(resource = Permission.Resource.RAG, action = Permission.Action.DELETE)
    @DeleteMapping("/{docId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a RAG document")
    public void delete(@PathVariable("id") UUID tenantId, @PathVariable UUID docId) {
        tenantAccessGuard.requireAccess(tenantId);
        ragService.delete(tenantId, docId);
    }

    /** One approval request for the whole batch, not one per document - see RagService#setActiveBulk. */
    @RequirePermission(resource = Permission.Resource.RAG, action = Permission.Action.UPDATE)
    @PutMapping("/bulk-status")
    @Operation(summary = "Pause or resume several documents at once")
    public List<RagDocumentResponse> bulkStatus(@PathVariable("id") UUID tenantId,
                                                @Valid @RequestBody RagBulkStatusRequest request) {
        tenantAccessGuard.requireAccess(tenantId);
        return ragService.setActiveBulk(tenantId, request.ids(), request.active()).stream()
                .map(RagDocumentResponse::from).toList();
    }

    @RequirePermission(resource = Permission.Resource.RAG, action = Permission.Action.DELETE)
    @DeleteMapping("/bulk")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete several documents at once")
    public void bulkDelete(@PathVariable("id") UUID tenantId, @Valid @RequestBody RagBulkDeleteRequest request) {
        tenantAccessGuard.requireAccess(tenantId);
        ragService.deleteBulk(tenantId, request.ids());
    }

    /** Preview-only: never counted as a hit, never written anywhere - see RagService#findSimilar. */
    @RequirePermission(resource = Permission.Resource.RAG, action = Permission.Action.READ)
    @PostMapping("/similar")
    @Operation(summary = "The closest existing document to a piece of text, if one is close enough to likely be a duplicate")
    public List<RagDocumentResponse> similar(@PathVariable("id") UUID tenantId,
                                             @Valid @RequestBody RagSimilarityCheckRequest request) {
        tenantAccessGuard.requireAccess(tenantId);
        return ragService.findSimilar(tenantId, request.content()).stream()
                .map(RagDocumentResponse::from).toList();
    }
}
