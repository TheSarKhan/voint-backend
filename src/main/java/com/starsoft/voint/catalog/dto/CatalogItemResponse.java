package com.starsoft.voint.catalog.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.starsoft.voint.catalog.CatalogItem;

public record CatalogItemResponse(
        UUID id,
        UUID tenantId,
        String sku,
        String name,
        String category,
        String itemType,
        BigDecimal price,
        BigDecimal priceDaily,
        BigDecimal priceMonthly,
        BigDecimal priceHourly,
        BigDecimal deposit,
        String currency,
        String unit,
        Integer durationMinutes,
        boolean inStock,
        Integer stockQuantity,
        String specs,
        String description,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static CatalogItemResponse from(CatalogItem item) {
        return new CatalogItemResponse(
                item.getId(),
                item.getTenantId(),
                item.getSku(),
                item.getName(),
                item.getCategory(),
                item.getItemType(),
                item.getPrice(),
                item.getPriceDaily(),
                item.getPriceMonthly(),
                item.getPriceHourly(),
                item.getDeposit(),
                item.getCurrency(),
                item.getUnit(),
                item.getDurationMinutes(),
                item.isInStock(),
                item.getStockQuantity(),
                item.getSpecs(),
                item.getDescription(),
                item.isActive(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }
}
