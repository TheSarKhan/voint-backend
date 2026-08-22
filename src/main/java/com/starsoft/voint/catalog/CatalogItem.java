package com.starsoft.voint.catalog;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "catalog_items")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatalogItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "sku", length = 64)
    private String sku;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "category", length = 128)
    private String category;

    @Builder.Default
    @Column(name = "item_type", length = 32, nullable = false)
    private String itemType = "SERVICE"; // SERVICE, PRODUCT, FOOD_DRINK, RENTAL

    @Column(name = "price", precision = 12, scale = 2)
    private BigDecimal price; // Universal base price

    @Column(name = "price_daily", precision = 12, scale = 2)
    private BigDecimal priceDaily;

    @Column(name = "price_monthly", precision = 12, scale = 2)
    private BigDecimal priceMonthly;

    @Column(name = "price_hourly", precision = 12, scale = 2)
    private BigDecimal priceHourly;

    @Column(name = "deposit", precision = 12, scale = 2)
    private BigDecimal deposit;

    @Builder.Default
    @Column(name = "currency", length = 8, nullable = false)
    private String currency = "AZN";

    @Builder.Default
    @Column(name = "unit", length = 32)
    private String unit = "ədəd";

    @Column(name = "duration_minutes")
    private Integer durationMinutes; // e.g. 30 mins for haircut / consultation / medical visit

    @Builder.Default
    @Column(name = "in_stock", nullable = false)
    private boolean inStock = true;

    @Builder.Default
    @Column(name = "stock_quantity")
    private Integer stockQuantity = 1;

    @Column(name = "specs", columnDefinition = "TEXT")
    private String specs;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Builder.Default
    @Column(name = "active", nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
