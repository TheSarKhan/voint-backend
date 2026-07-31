package com.starsoft.voint.question;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.starsoft.voint.auth.TenantAccessGuard;
import com.starsoft.voint.question.dto.AnswerQuestionRequest;
import com.starsoft.voint.question.dto.DraftAnswerResponse;
import com.starsoft.voint.question.dto.UnansweredQuestionResponse;
import com.starsoft.voint.rbac.Permission;
import com.starsoft.voint.rbac.RequirePermission;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Bilik bazasındakı boşluqlar: agentin cavablaya bilmədiyi suallar və onların bağlanması.
 *
 * <p>İcazələr mövcud lüğətdən götürülür, yeni resurs yaradılmır: oxumaq zəngin bir hissəsidir
 * (CALL:READ), cavab yazmaq isə bilik bazasına sənəd əlavə etməkdir (RAG:CREATE) — çünki
 * faktiki olaraq baş verən budur.
 */
@RestController
@RequestMapping("/api/v1/tenants/{id}/questions")
@RequiredArgsConstructor
@Tag(name = "Unanswered questions", description = "Knowledge-base gaps found by post-call analysis")
public class UnansweredQuestionController {

    private final UnansweredQuestionService service;
    private final TenantAccessGuard tenantAccessGuard;

    @RequirePermission(resource = Permission.Resource.CALL, action = Permission.Action.READ)
    @GetMapping
    @Operation(summary = "List unanswered questions of the tenant (optionally filtered by status)")
    public List<UnansweredQuestionResponse> list(@PathVariable("id") UUID tenantId,
                                                 @RequestParam(required = false) QuestionStatus status) {
        tenantAccessGuard.requireAccess(tenantId);
        return service.list(tenantId, status).stream().map(UnansweredQuestionResponse::from).toList();
    }

    @RequirePermission(resource = Permission.Resource.RAG, action = Permission.Action.READ)
    @PostMapping("/{questionId}/draft")
    @Operation(summary = "Ask the AI for a draft answer (nothing is stored - the operator confirms it)")
    public DraftAnswerResponse draft(@PathVariable("id") UUID tenantId,
                                     @PathVariable UUID questionId) {
        tenantAccessGuard.requireAccess(tenantId);
        return service.draft(tenantId, questionId);
    }

    @RequirePermission(resource = Permission.Resource.RAG, action = Permission.Action.CREATE)
    @PostMapping("/{questionId}/answer")
    @Operation(summary = "Save the answer into the knowledge base and close the question")
    public UnansweredQuestionResponse answer(@PathVariable("id") UUID tenantId,
                                             @PathVariable UUID questionId,
                                             @Valid @RequestBody AnswerQuestionRequest request) {
        tenantAccessGuard.requireAccess(tenantId);
        return UnansweredQuestionResponse.from(service.answer(tenantId, questionId, request));
    }

    @RequirePermission(resource = Permission.Resource.CALL, action = Permission.Action.UPDATE)
    @PostMapping("/{questionId}/dismiss")
    @Operation(summary = "Close the question without adding anything to the knowledge base")
    public UnansweredQuestionResponse dismiss(@PathVariable("id") UUID tenantId,
                                              @PathVariable UUID questionId) {
        tenantAccessGuard.requireAccess(tenantId);
        return UnansweredQuestionResponse.from(service.dismiss(tenantId, questionId));
    }
}
