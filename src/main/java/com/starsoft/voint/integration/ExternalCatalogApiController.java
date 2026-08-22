package com.starsoft.voint.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.starsoft.voint.catalog.CatalogItem;
import com.starsoft.voint.catalog.CatalogItemRepository;
import com.starsoft.voint.catalog.CatalogService;
import com.starsoft.voint.catalog.dto.CatalogItemCreateRequest;
import com.starsoft.voint.catalog.dto.CatalogItemResponse;
import com.starsoft.voint.catalog.dto.CatalogItemUpdateRequest;
import com.starsoft.voint.rbac.PublicEndpoint;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/external/catalog")
@RequiredArgsConstructor
@Tag(name = "External API (1C / ERP)", description = "1C, ERP və xarici sistemlər üçün Açıq REST API (X-Voint-Api-Key ilə qorunur)")
public class ExternalCatalogApiController {

    private final CatalogService catalogService;
    private final CatalogItemRepository catalogRepository;

    public record BulkSyncRequest(
            boolean replaceAll,
            List<CatalogItemCreateRequest> items
    ) {}

    public record BulkSyncResponse(
            int processedCount,
            int createdCount,
            int updatedCount,
            String message
    ) {}

    @PublicEndpoint("Xarici REST API - ApiKeyAuthFilter (X-Voint-Api-Key) ilə qorunur")
    @GetMapping("/items")
    @Operation(summary = "Kataloq məhsullarını gətir (1C/ERP)")
    public List<CatalogItemResponse> listItems(@RequestAttribute(ApiKeyAuthFilter.TENANT_ID_ATTR) UUID tenantId) {
        return catalogService.list(tenantId).stream().map(CatalogItemResponse::from).toList();
    }

    @PublicEndpoint("Xarici REST API - ApiKeyAuthFilter (X-Voint-Api-Key) ilə qorunur")
    @PostMapping("/items")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Tək məhsulu əlavə et və ya SKU üzrə yenilə (Upsert)")
    public CatalogItemResponse upsertItem(@RequestAttribute(ApiKeyAuthFilter.TENANT_ID_ATTR) UUID tenantId,
                                         @Valid @RequestBody CatalogItemCreateRequest request) {

        if (request.sku() != null && !request.sku().isBlank()) {
            Optional<CatalogItem> existing = catalogRepository.findByTenantIdAndSku(tenantId, request.sku().trim());
            if (existing.isPresent()) {
                CatalogItem item = existing.get();
                CatalogItemUpdateRequest update = new CatalogItemUpdateRequest(
                        request.sku(),
                        request.name(),
                        request.category(),
                        request.itemType(),
                        request.price(),
                        request.priceDaily(),
                        request.priceMonthly(),
                        request.priceHourly(),
                        request.deposit(),
                        request.currency(),
                        request.unit(),
                        request.durationMinutes(),
                        request.inStock(),
                        request.stockQuantity(),
                        request.specs(),
                        request.description(),
                        true
                );
                return CatalogItemResponse.from(catalogService.update(tenantId, item.getId(), update));
            }
        }

        return CatalogItemResponse.from(catalogService.create(tenantId, request));
    }

    @PublicEndpoint("Xarici REST API - ApiKeyAuthFilter (X-Voint-Api-Key) ilə qorunur")
    @PostMapping("/bulk-sync")
    @Operation(summary = "1C / ERP-dən toplu anbar və qiymət sinxronizasiyası")
    public BulkSyncResponse bulkSync(@RequestAttribute(ApiKeyAuthFilter.TENANT_ID_ATTR) UUID tenantId,
                                     @Valid @RequestBody BulkSyncRequest request) {

        if (request.items == null || request.items.isEmpty()) {
            return new BulkSyncResponse(0, 0, 0, "Heç bir məhsul göndərilməyib");
        }

        if (request.replaceAll()) {
            List<CatalogItem> oldList = catalogRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
            catalogRepository.deleteAll(oldList);
            log.info("Cleared {} old catalog items for tenant {} before bulk-sync", oldList.size(), tenantId);
        }

        int created = 0;
        int updated = 0;

        for (CatalogItemCreateRequest itemReq : request.items()) {
            if (itemReq.name() == null || itemReq.name().isBlank()) continue;

            if (itemReq.sku() != null && !itemReq.sku().isBlank() && !request.replaceAll()) {
                Optional<CatalogItem> existing = catalogRepository.findByTenantIdAndSku(tenantId, itemReq.sku().trim());
                if (existing.isPresent()) {
                    CatalogItem item = existing.get();
                    CatalogItemUpdateRequest update = new CatalogItemUpdateRequest(
                            itemReq.sku(),
                            itemReq.name(),
                            itemReq.category(),
                            itemReq.itemType(),
                            itemReq.price(),
                            itemReq.priceDaily(),
                            itemReq.priceMonthly(),
                            itemReq.priceHourly(),
                            itemReq.deposit(),
                            itemReq.currency(),
                            itemReq.unit(),
                            itemReq.durationMinutes(),
                            itemReq.inStock(),
                            itemReq.stockQuantity(),
                            itemReq.specs(),
                            itemReq.description(),
                            true
                    );
                    catalogService.update(tenantId, item.getId(), update);
                    updated++;
                    continue;
                }
            }

            catalogService.create(tenantId, itemReq);
            created++;
        }

        log.info("1C Bulk sync completed for tenant {}: {} created, {} updated", tenantId, created, updated);
        return new BulkSyncResponse(created + updated, created, updated, "Sinxronizasiya uğurla tamamlandı");
    }

    @PublicEndpoint("Xarici REST API - ApiKeyAuthFilter (X-Voint-Api-Key) ilə qorunur")
    @DeleteMapping("/items/{sku}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "SKU üzrə məhsulu sil")
    public void deleteBySku(@RequestAttribute(ApiKeyAuthFilter.TENANT_ID_ATTR) UUID tenantId,
                            @PathVariable("sku") String sku) {
        CatalogItem item = catalogRepository.findByTenantIdAndSku(tenantId, sku.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "SKU ilə məhsul tapılmadı: " + sku));
        catalogService.delete(tenantId, item.getId());
    }
}
