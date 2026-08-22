package com.starsoft.voint.catalog;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.starsoft.voint.catalog.dto.CatalogItemCreateRequest;
import com.starsoft.voint.catalog.dto.CatalogItemUpdateRequest;
import com.starsoft.voint.catalog.dto.CatalogStockUpdateRequest;
import com.starsoft.voint.llm.GeminiApiClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogService {

    private final CatalogItemRepository catalogRepository;
    private final GeminiApiClient geminiApiClient;

    @Transactional(readOnly = true)
    public List<CatalogItem> list(UUID tenantId) {
        return catalogRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    @Transactional(readOnly = true)
    public List<CatalogItem> listActive(UUID tenantId) {
        return catalogRepository.findByTenantIdAndActiveTrueOrderByCategoryAscNameAsc(tenantId);
    }

    @Transactional(readOnly = true)
    public CatalogItem get(UUID tenantId, UUID id) {
        return catalogRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kataloq məhsulu tapılmadı"));
    }

    @Transactional
    public CatalogItem create(UUID tenantId, CatalogItemCreateRequest request) {
        String itemType = request.itemType() != null && !request.itemType().isBlank()
                ? request.itemType().trim()
                : (request.priceDaily() != null ? "RENTAL" : "SERVICE");

        BigDecimal price = request.price() != null
                ? request.price()
                : (request.priceDaily() != null ? request.priceDaily() : request.priceHourly());

        String unit = request.unit() != null && !request.unit().isBlank()
                ? request.unit().trim()
                : ("RENTAL".equals(itemType) ? "gün" : "ədəd");

        CatalogItem item = CatalogItem.builder()
                .tenantId(tenantId)
                .sku(request.sku())
                .name(request.name().trim())
                .category(request.category() != null ? request.category().trim() : "Ümumi")
                .itemType(itemType)
                .price(price)
                .priceDaily(request.priceDaily() != null ? request.priceDaily() : ("RENTAL".equals(itemType) ? price : null))
                .priceMonthly(request.priceMonthly())
                .priceHourly(request.priceHourly())
                .deposit(request.deposit())
                .currency(request.currency() != null && !request.currency().isBlank() ? request.currency().trim() : "AZN")
                .unit(unit)
                .durationMinutes(request.durationMinutes())
                .inStock(request.inStock() != null ? request.inStock() : true)
                .stockQuantity(request.stockQuantity() != null ? request.stockQuantity() : 1)
                .specs(request.specs())
                .description(request.description())
                .active(true)
                .build();

        CatalogItem saved = catalogRepository.save(item);
        embedItem(saved);
        return saved;
    }

    @Transactional
    public CatalogItem update(UUID tenantId, UUID id, CatalogItemUpdateRequest request) {
        CatalogItem item = get(tenantId, id);

        if (request.sku() != null) item.setSku(request.sku().trim());
        if (request.name() != null) item.setName(request.name().trim());
        if (request.category() != null) item.setCategory(request.category().trim());
        if (request.itemType() != null) item.setItemType(request.itemType().trim());
        if (request.price() != null) item.setPrice(request.price());
        if (request.priceDaily() != null) item.setPriceDaily(request.priceDaily());
        if (request.priceMonthly() != null) item.setPriceMonthly(request.priceMonthly());
        if (request.priceHourly() != null) item.setPriceHourly(request.priceHourly());
        if (request.deposit() != null) item.setDeposit(request.deposit());
        if (request.currency() != null) item.setCurrency(request.currency().trim());
        if (request.unit() != null) item.setUnit(request.unit().trim());
        if (request.durationMinutes() != null) item.setDurationMinutes(request.durationMinutes());
        if (request.inStock() != null) item.setInStock(request.inStock());
        if (request.stockQuantity() != null) item.setStockQuantity(request.stockQuantity());
        if (request.specs() != null) item.setSpecs(request.specs().trim());
        if (request.description() != null) item.setDescription(request.description().trim());
        if (request.active() != null) item.setActive(request.active());

        // Keep price synced with priceDaily if one is missing
        if (item.getPrice() == null && item.getPriceDaily() != null) {
            item.setPrice(item.getPriceDaily());
        }

        CatalogItem saved = catalogRepository.save(item);
        embedItem(saved);
        return saved;
    }

    @Transactional
    public CatalogItem updateStock(UUID tenantId, UUID id, CatalogStockUpdateRequest request) {
        CatalogItem item = get(tenantId, id);
        if (request.inStock() != null) {
            item.setInStock(request.inStock());
        }
        if (request.stockQuantity() != null) {
            item.setStockQuantity(request.stockQuantity());
        }
        return catalogRepository.save(item);
    }

    @Transactional
    public void delete(UUID tenantId, UUID id) {
        CatalogItem item = get(tenantId, id);
        catalogRepository.delete(item);
    }

    @Transactional
    public List<CatalogItem> importFile(UUID tenantId, MultipartFile file) {
        List<CatalogFileExtractor.ExtractedCatalogItem> extracted = CatalogFileExtractor.extract(file);
        if (extracted.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Fayldan heç bir məhsul tapılmadı");
        }

        List<CatalogItem> savedList = new ArrayList<>();
        for (CatalogFileExtractor.ExtractedCatalogItem row : extracted) {
            BigDecimal price = row.priceDaily() != null ? row.priceDaily() : row.priceHourly();
            CatalogItem item = CatalogItem.builder()
                    .tenantId(tenantId)
                    .sku(row.sku())
                    .name(row.name())
                    .category(row.category() != null ? row.category() : "Ümumi")
                    .itemType(row.priceDaily() != null ? "RENTAL" : "SERVICE")
                    .price(price)
                    .priceDaily(row.priceDaily())
                    .priceMonthly(row.priceMonthly())
                    .priceHourly(row.priceHourly())
                    .deposit(row.deposit())
                    .unit(row.unit() != null ? row.unit() : "ədəd")
                    .inStock(row.inStock())
                    .stockQuantity(row.stockQuantity() != null ? row.stockQuantity() : 1)
                    .specs(row.specs())
                    .description(row.description())
                    .active(true)
                    .build();

            CatalogItem saved = catalogRepository.save(item);
            embedItem(saved);
            savedList.add(saved);
        }
        log.info("Imported {} catalog items for tenant {}", savedList.size(), tenantId);
        return savedList;
    }

    @Transactional(readOnly = true)
    public List<CatalogItem> findSimilar(UUID tenantId, String query, int limit) {
        if (query == null || query.isBlank()) {
            return listActive(tenantId).stream().limit(limit).toList();
        }
        try {
            float[] vector = geminiApiClient.embedContent(query);
            if (vector == null) {
                return listActive(tenantId).stream().limit(limit).toList();
            }
            return catalogRepository.findSimilar(tenantId, formatVector(vector), limit);
        } catch (Exception e) {
            log.warn("Vector search failed on catalog for tenant {}: {}", tenantId, e.getMessage());
            return listActive(tenantId).stream().limit(limit).toList();
        }
    }

    private void embedItem(CatalogItem item) {
        StringBuilder textToEmbed = new StringBuilder();
        textToEmbed.append(item.getName()).append(" ");
        if (item.getItemType() != null) textToEmbed.append(item.getItemType()).append(" ");
        if (item.getCategory() != null) textToEmbed.append(item.getCategory()).append(" ");
        if (item.getPrice() != null) textToEmbed.append("Qiymət: ").append(item.getPrice()).append(" ").append(item.getCurrency()).append(" ");
        if (item.getDurationMinutes() != null) textToEmbed.append("Müddət: ").append(item.getDurationMinutes()).append(" dəqiqə ");
        if (item.getSpecs() != null) textToEmbed.append(item.getSpecs()).append(" ");
        if (item.getDescription() != null) textToEmbed.append(item.getDescription()).append(" ");
        if (item.getPriceDaily() != null) textToEmbed.append("Günlük: ").append(item.getPriceDaily()).append(" AZN ");
        if (item.getPriceMonthly() != null) textToEmbed.append("Aylıq: ").append(item.getPriceMonthly()).append(" AZN ");

        try {
            float[] vector = geminiApiClient.embedContent(textToEmbed.toString().trim());
            if (vector != null) {
                catalogRepository.updateEmbedding(item.getId(), formatVector(vector));
            }
        } catch (Exception e) {
            log.warn("Failed to generate embedding for catalog item '{}': {}", item.getName(), e.getMessage());
        }
    }

    private String formatVector(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }
}
