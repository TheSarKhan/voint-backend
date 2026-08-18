package com.starsoft.voint.rag;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.starsoft.voint.auth.TenantAccessGuard;
import com.starsoft.voint.rag.dto.RagChatReplyResponse;
import com.starsoft.voint.rag.dto.RagChatRequest;
import com.starsoft.voint.rag.dto.RagDocumentResponse;
import com.starsoft.voint.rbac.Permission;
import com.starsoft.voint.rbac.RequirePermission;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Builds a tenant's knowledge base through conversation instead of hand-written documents. Two
 * steps, matching {@link RagChatService}: {@code /chat} carries the conversation forward one turn
 * at a time, {@code /chat/finish} reads the whole thing once and files it as real RAG documents.
 * Stateless - the panel resends the growing message list with every call, same shape the Vapi
 * custom-LLM webhook already uses for call history.
 */
@RestController
@RequestMapping("/api/v1/tenants/{id}/rag/chat")
@RequiredArgsConstructor
@Tag(name = "RAG", description = "Conversational knowledge-base building")
public class RagChatController {

    private final RagChatService ragChatService;
    private final TenantAccessGuard tenantAccessGuard;

    @RequirePermission(resource = Permission.Resource.RAG, action = Permission.Action.READ)
    @PostMapping
    @Operation(summary = "Next interviewer reply, given the conversation so far")
    public RagChatReplyResponse chat(@PathVariable("id") UUID tenantId, @Valid @RequestBody RagChatRequest request) {
        tenantAccessGuard.requireAccess(tenantId);
        try {
            return new RagChatReplyResponse(ragChatService.reply(request.history()));
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage(), e);
        }
    }

    @RequirePermission(resource = Permission.Resource.RAG, action = Permission.Action.CREATE)
    @PostMapping("/finish")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Extract the conversation into RAG documents and save them")
    public List<RagDocumentResponse> finish(@PathVariable("id") UUID tenantId, @Valid @RequestBody RagChatRequest request) {
        tenantAccessGuard.requireAccess(tenantId);
        try {
            return ragChatService.extractAndSave(tenantId, request.history()).stream()
                    .map(RagDocumentResponse::from)
                    .toList();
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage(), e);
        }
    }
}
