package com.starsoft.voint.catalog.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CatalogItemUpdateRequest(
        @Size(max = 64) String sku,
        @NotBlank @Size(max = 255) String name,
        @Size(max = 128) String category,
        String itemType,
        BigDecimal price,
        BigDecimal priceDaily,
        BigDecimal priceMonthly,
        BigDecimal priceHourly,
        BigDecimal deposit,
        String currency,
        String unit,
        Integer durationMinutes,
        Boolean inStock,
        Integer stockQuantity,
        String specs,
        String description,
        Boolean active
) {}
