package com.starsoft.voint.rag;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Turns a tenant's own file (their price list, FAQ doc...) into plain text for the knowledge
 * base, so they don't have to retype what they already have written down somewhere.
 *
 * <p>Dispatches on the file's extension rather than its declared content-type: browsers and OS
 * file pickers are inconsistent about what content-type a .docx or .txt actually gets sent as,
 * but the extension the tenant sees in their own file manager is reliable.
 */
final class RagFileExtractor {

    private RagFileExtractor() {
    }

    static String extract(MultipartFile file) {
        String name = file.getOriginalFilename();
        String ext = extension(name);
        try (InputStream in = file.getInputStream()) {
            String text = switch (ext) {
                case "txt" -> new String(in.readAllBytes(), StandardCharsets.UTF_8);
                case "docx" -> extractDocx(in);
                case "pdf" -> extractPdf(in);
                default -> throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                        "Yalnız .txt, .docx və .pdf faylları dəstəklənir.");
            };
            if (text.isBlank()) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Fayldan mətn çıxarıla bilmədi - boş və ya skan edilmiş şəkil ola bilər.");
            }
            return text.strip();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Fayl oxuna bilmədi.", e);
        }
    }

    private static String extractDocx(InputStream in) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(in);
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return extractor.getText();
        }
    }

    private static String extractPdf(InputStream in) throws IOException {
        byte[] bytes = in.readAllBytes();
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    private static String extension(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
