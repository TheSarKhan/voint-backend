package com.starsoft.voint.catalog;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
import com.starsoft.voint.catalog.dto.CatalogItemCreateRequest;
import com.starsoft.voint.catalog.dto.CatalogItemResponse;
import com.starsoft.voint.catalog.dto.CatalogItemUpdateRequest;
import com.starsoft.voint.catalog.dto.CatalogStockUpdateRequest;
import com.starsoft.voint.rbac.Permission;
import com.starsoft.voint.rbac.RequirePermission;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/tenants/{id}/catalog")
@RequiredArgsConstructor
@Tag(name = "Catalog", description = "Məhsul və Qiymət Kataloqu İdarəetməsi")
public class CatalogController {

    private final CatalogService catalogService;
    private final TenantAccessGuard tenantAccessGuard;

    @RequirePermission(resource = Permission.Resource.RAG, action = Permission.Action.READ)
    @GetMapping
    @Operation(summary = "Müəssisənin bütün kataloq məhsullarını gətir")
    public List<CatalogItemResponse> list(@PathVariable("id") UUID tenantId) {
        tenantAccessGuard.requireAccess(tenantId);
        return catalogService.list(tenantId).stream().map(CatalogItemResponse::from).toList();
    }

    @RequirePermission(resource = Permission.Resource.RAG, action = Permission.Action.READ)
    @GetMapping("/{itemId}")
    @Operation(summary = "Kataloq məhsulunun detallarını gətir")
    public CatalogItemResponse get(@PathVariable("id") UUID tenantId, @PathVariable UUID itemId) {
        tenantAccessGuard.requireAccess(tenantId);
        return CatalogItemResponse.from(catalogService.get(tenantId, itemId));
    }

    @RequirePermission(resource = Permission.Resource.RAG, action = Permission.Action.CREATE)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Yeni kataloq məhsulu əlavə et")
    public CatalogItemResponse create(@PathVariable("id") UUID tenantId,
                                      @Valid @RequestBody CatalogItemCreateRequest request) {
        tenantAccessGuard.requireAccess(tenantId);
        return CatalogItemResponse.from(catalogService.create(tenantId, request));
    }

    @RequirePermission(resource = Permission.Resource.RAG, action = Permission.Action.UPDATE)
    @PutMapping("/{itemId}")
    @Operation(summary = "Kataloq məhsulunu redaktə et")
    public CatalogItemResponse update(@PathVariable("id") UUID tenantId,
                                      @PathVariable UUID itemId,
                                      @Valid @RequestBody CatalogItemUpdateRequest request) {
        tenantAccessGuard.requireAccess(tenantId);
        return CatalogItemResponse.from(catalogService.update(tenantId, itemId, request));
    }

    @RequirePermission(resource = Permission.Resource.RAG, action = Permission.Action.UPDATE)
    @PatchMapping("/{itemId}/stock")
    @Operation(summary = "Məhsulun stok vəziyyətini (in_stock) tez dəyiş")
    public CatalogItemResponse updateStock(@PathVariable("id") UUID tenantId,
                                           @PathVariable UUID itemId,
                                           @RequestBody CatalogStockUpdateRequest request) {
        tenantAccessGuard.requireAccess(tenantId);
        return CatalogItemResponse.from(catalogService.updateStock(tenantId, itemId, request));
    }

    @RequirePermission(resource = Permission.Resource.RAG, action = Permission.Action.DELETE)
    @DeleteMapping("/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Kataloq məhsulunu sil")
    public void delete(@PathVariable("id") UUID tenantId, @PathVariable UUID itemId) {
        tenantAccessGuard.requireAccess(tenantId);
        catalogService.delete(tenantId, itemId);
    }

    @RequirePermission(resource = Permission.Resource.RAG, action = Permission.Action.CREATE)
    @PostMapping("/upload")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Excel, CSV, PDF və ya Word faylından toplu kataloq yüklə")
    public List<CatalogItemResponse> upload(@PathVariable("id") UUID tenantId,
                                            @RequestParam("file") MultipartFile file) {
        tenantAccessGuard.requireAccess(tenantId);
        return catalogService.importFile(tenantId, file).stream().map(CatalogItemResponse::from).toList();
    }
}
