package com.starsoft.voint.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class CatalogFileExtractorTest {

    @Test
    void testExtractCsvCatalog() {
        String csvContent = """
                Məhsul Adı,Günlük Qiymət,Kateqoriya,Təsvir
                JCB 3CX Ekskavator,350,Ağır Texnika,Çalov 1m3
                CAT 320 Paletli,600,Ekskavatorlar,22 tonluq qazıntı
                Komatsu D65 Buldozer,400,Buldozerlər,Torpaq hamarlama
                """;

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "texnika_kataloq.csv",
                "text/csv",
                csvContent.getBytes(StandardCharsets.UTF_8)
        );

        List<CatalogFileExtractor.ExtractedCatalogItem> items = CatalogFileExtractor.extract(file);
        assertNotNull(items);
        assertEquals(3, items.size());

        CatalogFileExtractor.ExtractedCatalogItem item1 = items.get(0);
        assertEquals("JCB 3CX Ekskavator", item1.name());
        assertEquals(new BigDecimal("350"), item1.priceDaily());
        assertEquals("Ağır Texnika", item1.category());
        assertEquals("Çalov 1m3", item1.description());
    }

    @Test
    void testExtractTxtCatalog() {
        String txtContent = """
                XCMG 25 Tonluq Avtokran: 450 AZN günlük icarə
                Bobcat S530 Mini Yükləyici: 220 manat günə
                """;

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "qiymetler.txt",
                "text/plain",
                txtContent.getBytes(StandardCharsets.UTF_8)
        );

        List<CatalogFileExtractor.ExtractedCatalogItem> items = CatalogFileExtractor.extract(file);
        assertNotNull(items);
        assertFalse(items.isEmpty());
    }
}
