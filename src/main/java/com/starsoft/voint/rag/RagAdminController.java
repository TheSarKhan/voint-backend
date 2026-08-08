package com.starsoft.voint.rag;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.starsoft.voint.auth.TenantAccessGuard;
import com.starsoft.voint.rag.dto.RagBackfillResponse;
import com.starsoft.voint.rbac.Permission;
import com.starsoft.voint.rbac.RequirePermission;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** Platforma əməliyyatları: bilik bazası sənədlərinin embedding-ini əl ilə tamamlamaq. */
@RestController
@RequiredArgsConstructor
@Tag(name = "RAG", description = "Tenant knowledge base (pgvector-backed RAG documents)")
public class RagAdminController {

    private final RagService ragService;
    private final TenantAccessGuard tenantAccessGuard;

    // requireSuperAdmin() tek basina bes ederdi, amma RAG hem platform hem tenant terefinde
    // qranted olunan bir resurs oldugundan @RequirePermission(tenantScoped=false) annotasiyasi
    // tek basina kifayet etmir - PlatformUserController-deki eyni pattern.
    @RequirePermission(resource = Permission.Resource.RAG, action = Permission.Action.UPDATE, tenantScoped = false)
    @PostMapping("/api/v1/admin/rag/backfill-embeddings")
    @Operation(summary = "Embed every rag_documents row still missing its embedding, right now "
            + "(platform staff only) - normally only runs at startup")
    public RagBackfillResponse backfillEmbeddings() {
        tenantAccessGuard.requireSuperAdmin();
        return ragService.backfillMissingEmbeddings();
    }
}
