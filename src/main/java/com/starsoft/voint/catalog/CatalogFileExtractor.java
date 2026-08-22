package com.starsoft.voint.catalog;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class CatalogFileExtractor {

    private static final DataFormatter FORMATTER = new DataFormatter();

    public record ExtractedCatalogItem(
            String sku,
            String name,
            String category,
            BigDecimal priceDaily,
            BigDecimal priceMonthly,
            BigDecimal priceHourly,
            BigDecimal deposit,
            String unit,
            boolean inStock,
            Integer stockQuantity,
            String specs,
            String description
    ) {}

    private CatalogFileExtractor() {}

    public static List<ExtractedCatalogItem> extract(MultipartFile file) {
        String name = file.getOriginalFilename();
        String ext = extension(name);
        try (InputStream in = file.getInputStream()) {
            return switch (ext) {
                case "xlsx", "xls" -> extractExcel(in);
                case "csv" -> extractCsv(in);
                case "pdf" -> extractFromText(extractPdfText(in));
                case "docx" -> extractFromText(extractDocxText(in));
                case "txt" -> extractFromText(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                default -> throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                        "Yalnız .xlsx, .xls, .csv, .pdf, .docx və .txt faylları dəstəklənir.");
            };
        } catch (IOException e) {
            log.error("Failed to parse catalog file {}", name, e);
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Kataloq faylı oxuna bilmədi: " + e.getMessage(), e);
        }
    }

    private static List<ExtractedCatalogItem> extractExcel(InputStream in) throws IOException {
        List<ExtractedCatalogItem> items = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) return items;

            Iterator<Row> rowIterator = sheet.iterator();
            if (!rowIterator.hasNext()) return items;

            // Header mapping
            Row headerRow = rowIterator.next();
            int colSku = -1, colName = -1, colCategory = -1, colPriceDaily = -1, colPriceMonthly = -1;
            int colDeposit = -1, colStock = -1, colSpecs = -1, colDesc = -1;

            for (Cell cell : headerRow) {
                String val = FORMATTER.formatCellValue(cell).trim().toLowerCase(Locale.ROOT);
                if (val.contains("sku") || val.contains("kod") || val.contains("artikul")) colSku = cell.getColumnIndex();
                else if (val.contains("ad") || val.contains("model") || val.contains("məhsul") || val.contains("texnika") || val.contains("name")) colName = cell.getColumnIndex();
                else if (val.contains("kateqoriya") || val.contains("növ") || val.contains("category")) colCategory = cell.getColumnIndex();
                else if (val.contains("günlük") || val.contains("gün") || val.contains("daily") || val.contains("qiymət") || val.contains("tarif") || val.contains("price")) {
                    if (colPriceDaily == -1) colPriceDaily = cell.getColumnIndex();
                } else if (val.contains("aylıq") || val.contains("ay") || val.contains("monthly")) colPriceMonthly = cell.getColumnIndex();
                else if (val.contains("depozit") || val.contains("girov") || val.contains("deposit")) colDeposit = cell.getColumnIndex();
                else if (val.contains("say") || val.contains("stok") || val.contains("qalıq") || val.contains("stock") || val.contains("status")) colStock = cell.getColumnIndex();
                else if (val.contains("parametr") || val.contains("xüsusiyyət") || val.contains("spesifikasiya") || val.contains("specs")) colSpecs = cell.getColumnIndex();
                else if (val.contains("təsvir") || val.contains("açıqlama") || val.contains("qeyd") || val.contains("desc")) colDesc = cell.getColumnIndex();
            }

            if (colName == -1 && colPriceDaily == -1) {
                // Fallback: assume column 0 is Name, column 1 is Category/Specs, column 2 is Price
                colName = 0;
                colCategory = 1;
                colPriceDaily = 2;
            }

            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                String name = getCellString(row, colName);
                if (name == null || name.isBlank()) continue;

                String sku = getCellString(row, colSku);
                String category = getCellString(row, colCategory);
                BigDecimal priceDaily = getCellBigDecimal(row, colPriceDaily);
                BigDecimal priceMonthly = getCellBigDecimal(row, colPriceMonthly);
                BigDecimal deposit = getCellBigDecimal(row, colDeposit);
                String specs = getCellString(row, colSpecs);
                String desc = getCellString(row, colDesc);

                items.add(new ExtractedCatalogItem(
                        sku,
                        name.trim(),
                        category != null ? category.trim() : "Ümumi",
                        priceDaily,
                        priceMonthly,
                        null,
                        deposit,
                        "gün",
                        true,
                        1,
                        specs,
                        desc
                ));
            }
        }
        return items;
    }

    private static List<ExtractedCatalogItem> extractCsv(InputStream in) throws IOException {
        List<ExtractedCatalogItem> items = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            boolean isHeader = true;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split("[,;\\t]");
                if (isHeader) {
                    isHeader = false;
                    // If line looks like headers, skip
                    if (line.toLowerCase().contains("ad") || line.toLowerCase().contains("qiymət") || line.toLowerCase().contains("name")) {
                        continue;
                    }
                }
                if (parts.length >= 2) {
                    String name = parts[0].trim().replace("\"", "");
                    BigDecimal price = parsePrice(parts.length > 1 ? parts[1] : null);
                    String category = parts.length > 2 ? parts[2].trim().replace("\"", "") : "Ümumi";
                    String desc = parts.length > 3 ? parts[3].trim().replace("\"", "") : null;
                    if (!name.isBlank()) {
                        items.add(new ExtractedCatalogItem(null, name, category, price, null, null, null, "gün", true, 1, null, desc));
                    }
                }
            }
        }
        return items;
    }

    private static List<ExtractedCatalogItem> extractFromText(String fullText) {
        List<ExtractedCatalogItem> items = new ArrayList<>();
        if (fullText == null || fullText.isBlank()) return items;

        String[] lines = fullText.split("\\r?\\n");
        for (String line : lines) {
            line = line.trim();
            if (line.length() < 5) continue;
            // Look for lines containing price patterns (e.g. "JCB 3CX: 350 AZN")
            if (line.contains("AZN") || line.contains("manat") || line.matches(".*\\d+\\s*(AZN|manat|₼).*")) {
                items.add(new ExtractedCatalogItem(null, line, "Kataloq", null, null, null, null, "gün", true, 1, null, line));
            }
        }
        if (items.isEmpty()) {
            items.add(new ExtractedCatalogItem(null, "Kataloq Məlumatı", "Ümumi", null, null, null, null, "gün", true, 1, null, fullText));
        }
        return items;
    }

    private static String extractPdfText(InputStream in) throws IOException {
        byte[] bytes = in.readAllBytes();
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    private static String extractDocxText(InputStream in) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(in);
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return extractor.getText();
        }
    }

    private static String getCellString(Row row, int colIdx) {
        if (colIdx < 0 || row == null) return null;
        Cell cell = row.getCell(colIdx);
        if (cell == null) return null;
        String val = FORMATTER.formatCellValue(cell);
        return val.isBlank() ? null : val;
    }

    private static BigDecimal getCellBigDecimal(Row row, int colIdx) {
        if (colIdx < 0 || row == null) return null;
        Cell cell = row.getCell(colIdx);
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue());
        }
        return parsePrice(FORMATTER.formatCellValue(cell));
    }

    private static BigDecimal parsePrice(String text) {
        if (text == null || text.isBlank()) return null;
        String cleaned = text.replaceAll("[^0-9.,]", "").replace(',', '.');
        try {
            return new BigDecimal(cleaned);
        } catch (Exception e) {
            return null;
        }
    }

    private static String extension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
