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

import com.starsoft.voint.auth.TenantAccessGuard;
import com.starsoft.voint.rag.dto.RagCategoryCreateRequest;
import com.starsoft.voint.rag.dto.RagCategoryResponse;
import com.starsoft.voint.rbac.Permission;
import com.starsoft.voint.rbac.RequirePermission;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Custom knowledge-base headings a tenant adds to the built-in topic list. */
@RestController
@RequestMapping("/api/v1/tenants/{id}/rag/categories")
@RequiredArgsConstructor
@Tag(name = "RAG", description = "Tenant knowledge base (pgvector-backed RAG documents)")
public class RagCategoryController {

    private final RagCategoryService categoryService;
    private final TenantAccessGuard tenantAccessGuard;

    @RequirePermission(resource = Permission.Resource.RAG, action = Permission.Action.READ)
    @GetMapping
    @Operation(summary = "This tenant's own custom knowledge-base headings")
    public List<RagCategoryResponse> list(@PathVariable("id") UUID tenantId) {
        tenantAccessGuard.requireAccess(tenantId);
        return categoryService.list(tenantId);
    }

    @RequirePermission(resource = Permission.Resource.RAG, action = Permission.Action.CREATE)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a custom heading")
    public RagCategoryResponse create(@PathVariable("id") UUID tenantId,
                                      @Valid @RequestBody RagCategoryCreateRequest request) {
        tenantAccessGuard.requireAccess(tenantId);
        return categoryService.create(tenantId, request);
    }

    @RequirePermission(resource = Permission.Resource.RAG, action = Permission.Action.DELETE)
    @DeleteMapping("/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a custom heading (documents filed under it are unaffected)")
    public void delete(@PathVariable("id") UUID tenantId, @PathVariable UUID categoryId) {
        tenantAccessGuard.requireAccess(tenantId);
        categoryService.delete(tenantId, categoryId);
    }
}
