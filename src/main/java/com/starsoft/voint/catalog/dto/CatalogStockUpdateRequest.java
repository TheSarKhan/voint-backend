package com.starsoft.voint.catalog.dto;

public record CatalogStockUpdateRequest(
        Boolean inStock,
        Integer stockQuantity
) {}
