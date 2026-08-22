package com.starsoft.voint.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.starsoft.voint.catalog.dto.CatalogItemCreateRequest;
import com.starsoft.voint.catalog.dto.CatalogItemUpdateRequest;
import com.starsoft.voint.catalog.dto.CatalogStockUpdateRequest;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class CatalogServiceTest {

    @Autowired
    private CatalogService catalogService;

    private static final UUID CES_TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void testUniversalBarbershopServiceCreation() {
        CatalogItemCreateRequest barberReq = new CatalogItemCreateRequest(
                "BARBER-01",
                "Klassik Saç Kəsimi və Styling",
                "Kişi Xidmətləri",
                "SERVICE",
                new BigDecimal("15.00"),
                null, null, null, null,
                "AZN",
                "seans",
                30,
                true,
                1,
                "Saç yuma, kəsim və fen daxildir",
                "Təcrübəli bərbərlər tərəfindən icra olunur"
        );

        CatalogItem created = catalogService.create(CES_TENANT_ID, barberReq);
        assertNotNull(created.getId());
        assertEquals("Klassik Saç Kəsimi və Styling", created.getName());
        assertEquals("SERVICE", created.getItemType());
        assertEquals(new BigDecimal("15.00"), created.getPrice());
        assertEquals(30, created.getDurationMinutes());
        assertEquals("seans", created.getUnit());
    }

    @Test
    void testUniversalRestaurantDishCreation() {
        CatalogItemCreateRequest dishReq = new CatalogItemCreateRequest(
                "DISH-07",
                "Quzu Ətindən Lülə Kabab",
                "Əsas Yeməklər",
                "FOOD_DRINK",
                new BigDecimal("9.50"),
                null, null, null, null,
                "AZN",
                "porsiya",
                null,
                true,
                1,
                "Quzu əti, quyruq, narşərab, təndir lavaşı",
                "Kömür manqalında bişirilir"
        );

        CatalogItem created = catalogService.create(CES_TENANT_ID, dishReq);
        assertNotNull(created.getId());
        assertEquals("Quzu Ətindən Lülə Kabab", created.getName());
        assertEquals("FOOD_DRINK", created.getItemType());
        assertEquals(new BigDecimal("9.50"), created.getPrice());
        assertEquals("porsiya", created.getUnit());
    }

    @Test
    void testCatalogCrudAndStockToggle() {
        CatalogItemCreateRequest createReq = new CatalogItemCreateRequest(
                "TEST-CRANE-50",
                "Liebherr 50 Tonluq Kran",
                "Kranlar",
                "RENTAL",
                new BigDecimal("800.00"),
                new BigDecimal("800.00"),
                new BigDecimal("18000.00"),
                null,
                new BigDecimal("1500.00"),
                "AZN",
                "gün",
                null,
                true,
                1,
                "Qol uzunluğu: 52 metr",
                "Ağır montaj işləri üçün avtokran"
        );

        CatalogItem created = catalogService.create(CES_TENANT_ID, createReq);
        assertNotNull(created.getId());
        assertEquals("Liebherr 50 Tonluq Kran", created.getName());
        assertEquals(new BigDecimal("800.00"), created.getPrice());
        assertTrue(created.isInStock());

        // Toggle stock to out of stock
        CatalogItem updatedStock = catalogService.updateStock(CES_TENANT_ID, created.getId(), new CatalogStockUpdateRequest(false, 0));
        assertFalse(updatedStock.isInStock());
        assertEquals(0, updatedStock.getStockQuantity());

        // Update item details
        CatalogItemUpdateRequest updateReq = new CatalogItemUpdateRequest(
                "TEST-CRANE-50",
                "Liebherr 50T Avtokran (Yeni)",
                "Kranlar",
                "RENTAL",
                new BigDecimal("850.00"),
                new BigDecimal("850.00"),
                new BigDecimal("19000.00"),
                null,
                new BigDecimal("1500.00"),
                "AZN",
                "gün",
                null,
                true,
                2,
                "Qol uzunluğu: 52 metr",
                "Ağır montaj işləri üçün avtokran",
                true
        );
        CatalogItem updated = catalogService.update(CES_TENANT_ID, created.getId(), updateReq);
        assertEquals("Liebherr 50T Avtokran (Yeni)", updated.getName());
        assertEquals(new BigDecimal("850.00"), updated.getPrice());

        // List
        List<CatalogItem> list = catalogService.list(CES_TENANT_ID);
        assertTrue(list.stream().anyMatch(i -> i.getId().equals(created.getId())));
    }

    @Test
    void testFindSimilarCatalogItems() {
        List<CatalogItem> similar = catalogService.findSimilar(CES_TENANT_ID, "JCB ekskavator icarəsi", 3);
        assertNotNull(similar);
        assertFalse(similar.isEmpty());
    }
}
