package com.starsoft.voint.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.starsoft.voint.llm.GeminiApiClient;
import com.starsoft.voint.question.QuestionStatus;
import com.starsoft.voint.question.UnansweredQuestion;
import com.starsoft.voint.question.UnansweredQuestionService;
import com.starsoft.voint.rag.dto.RagChatTurn;
import com.starsoft.voint.rag.dto.RagDocumentCreateRequest;
import com.starsoft.voint.tenant.Tenant;
import com.starsoft.voint.tenant.TenantRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Dynamic, industry-tailored knowledge base onboarding assistant.
 * Adapts to each business's specific sector (dental, heavy machinery, restaurant, legal, etc.),
 * avoids asking redundant questions already documented in RAG, and proactively probes for
 * industry-specific edge cases, customer disputes, warranties, emergency procedures, and policies.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagChatService {

    private static final int EXTRACT_MAX_TOKENS = 3000;
    private static final int THINKING_BUDGET = 0;

    private static final String INTERVIEWER_SYSTEM_PROMPT = """
            Sən Voint platformasının YÜKSƏK DƏRƏCƏDƏ İXTİSASLAŞMIŞ və SAHƏYƏ UYĞUN BİLİK BAZASI EKSPERTİSƏN.
            Sənin rolun hər müəssisəyə fərdi və unikal bir agent kimi yanaşmaqdır. Şablon və ya ümumi suallar vermirsən!

            ƏN ƏSAS VƏ QƏTİ QAYDALAR:
            1. SAHƏYƏ XAS DƏRİN SİTUASİYALAR: Müəssisənin profilinə bax (şirkət adı, fəaliyyət sahəsi, ixtisaslaşma mövzusu). Məhz BU SAHƏNİN real zənglərində müştərilərin soruşa biləcəyi ən kritik, spesifik və texniki sualları ver:
               * Texnika/İcarədirsə: Yanacaq, çatdırılma haqqı, operatordan istifadə, texnika sıradan çıxanda dəyişdirilmə müddəti, zədələnmə məsuliyyəti və s.
               * Klinika/Tibbdirsə: Sığorta qəbulu (Paşa, Atəşgah), təcili kəskin ağrılar, həkimlərin qəbul qrafiki, uşaq prosedurları, sterilizasiya və zəmanət.
               * Restoran/Qonaqpərvərlikdirsə: Masa rezervasiyası, depozit, menyu məhdudiyyətləri, banket və terras qaydaları.
               * Xidmət/Hüquq/Biznesdirsə: İlkin konsultasiya ödənişlidirmi, müqavilə şərtləri, məxfilik (NDA) və icra müddətləri.
               * Ticarət/Satışdırsa: Çatdırılma zonaları, geri qaytarma (14 gün) və dəyişdirmə şərtləri, zəmanət xidməti.
            2. MÖVCUD BİLİKLƏRİ TƏKRAR SORUŞMA: Aşağıda verilmiş "MÖVCUD BİLİK BAZASI"nda artıq qeyd olunmuş faktları (məs. artıq bildiyin iş saatlarını, xidmətləri və ya ünvanı) QƏTİYYƏN yenidən soruşma!
            3. REAL CAVABSIZ ZƏNG SUALLARINA ÜSTÜNLÜK VER: Əgər aşağıda real zənglərdən toplanmış cavabsız suallar varsa, ilk növbədə onları və oxşar situasiyaları aydınlaşdır.
            4. DİALOQ TƏRZİ: Hər zaman səmimi, konkret və peşəkar ol. Sahib cavab verəndə qısaca təsdiqlə və dərhal növbəti maraqlı, düşündürücü situasiyanı soruş.
            5. Bir dəfəyə YALNIZ 1 konkret sual ver.

            Cavabların QISA və CANLI olsun (2-4 cümlə) - bu süni intellekt mühazirəsi deyil, real biznes müsahibəsidir.
            """;

    private static final String EXTRACT_SYSTEM_PROMPT = """
            Sən bir söhbəti oxuyub, ordan bir biznes haqqında FAKTLARI çıxarırsan ki, bunlar
            müştərilərə telefonla cavab verən AI agentin bilik bazasına yazılsın.

            Hər fakt/qayda üçün ayrıca bir giriş yarat:
            - "title": qısa başlıq, bir neçə söz (məs. "İş saatları", "Çatdırılma şərtləri", "Ödəniş qaydaları", "Zəmanət və ləğvetmə")
            - "content": tam, özündən aydın cümlə(lər) - agent bunu OLDUĞU KİMİ müştəriyə deyə
              bilməlidir, söhbətin qalan hissəsini bilmədən də başa düşülməlidir.

            Qaydalar:
            - Yalnız söhbətdə HƏQİQƏTƏN deyilən şeyi yaz - heç nə uydurma, əlavə etmə, təxmin
              etmə.
            - Salamlaşma, kiçik danışıq, aydın olmayan/natamam fikirlər - bunları çıxarma.
            - Eyni mövzudakı faktları bir girişdə birləşdir, fərqli mövzuları qarışdırma.
            - İstifadə oluna bilən heç bir fakt yoxdursa, boş massiv qaytar.

            Cavabı YALNIZ bu JSON sxemində qaytar:
            {"entries": [{"title": "...", "content": "..."}]}

            Bütün mətn Azərbaycan dilində olsun.
            """;

    private final GeminiApiClient geminiApiClient;
    private final RagService ragService;
    private final UnansweredQuestionService questionService;
    private final TenantRepository tenantRepository;
    private final ObjectMapper objectMapper;

    /** The interviewer's next message, tailored dynamically to the tenant's exact industry. */
    public String reply(UUID tenantId, List<RagChatTurn> history) {
        requireGemini();
        String latest = history.get(history.size() - 1).content();

        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);

        StringBuilder profileContext = new StringBuilder();
        profileContext.append("\n\n--- MÜƏSSİSƏ PROFİLİ VƏ SAHƏSİ ---\n");
        if (tenant != null) {
            profileContext.append("• Şirkət Adı: ").append(tenant.getName()).append("\n");
            if (StringUtils.hasText(tenant.getSttDomain())) {
                profileContext.append("• Fəaliyyət Sahəsi (Domen): ").append(tenant.getSttDomain()).append("\n");
            }
            if (StringUtils.hasText(tenant.getSttTopic())) {
                profileContext.append("• Əsas İxtisaslaşma Mövzuları: ").append(tenant.getSttTopic()).append("\n");
            }
            if (StringUtils.hasText(tenant.getGreetingText())) {
                profileContext.append("• Zəng Salamlaması: ").append(tenant.getGreetingText()).append("\n");
            }
        }

        profileContext.append("\n--- MÖVCUD BİLİK BAZASI (BUNLAR ARTIQ MƏLUMDUR, TƏKRAR SORUŞMA!) ---\n");
        List<RagDocument> existingDocs = ragService.list(tenantId);
        if (existingDocs.isEmpty()) {
            profileContext.append("(Bilik bazası hələ boşdur)\n");
        } else {
            for (RagDocument d : existingDocs) {
                profileContext.append("• [").append(d.getCategory() != null ? d.getCategory() : "Məlumat")
                        .append("]: ").append(d.getContent()).append("\n");
            }
        }

        List<UnansweredQuestion> openQuestions = questionService.list(tenantId, QuestionStatus.OPEN);
        if (!openQuestions.isEmpty()) {
            profileContext.append("\n--- MÜŞTƏRİLƏRİN ZƏNGLƏRDƏ VERDİYİ CAVABSIZ SUALLAR (REAL BOŞLUQLAR!) ---\n");
            for (UnansweredQuestion q : openQuestions) {
                profileContext.append("• ").append(q.getQuestion()).append("\n");
            }
        }

        String systemPrompt = INTERVIEWER_SYSTEM_PROMPT + profileContext + "\n\nSÖHBƏT TARİXÇƏSİ:\n" + formatTranscript(history);
        GeminiApiClient.GenerationResult result = geminiApiClient.generateContent(systemPrompt, latest);
        return result.text();
    }

    /**
     * Reads the whole conversation and files each extracted fact as a real RAG document.
     */
    public List<RagDocument> extractAndSave(UUID tenantId, List<RagChatTurn> history) {
        requireGemini();
        String transcript = formatTranscript(history);
        GeminiApiClient.GenerationResult result =
                geminiApiClient.generateJson(EXTRACT_SYSTEM_PROMPT, transcript, EXTRACT_MAX_TOKENS, THINKING_BUDGET);

        JsonNode root;
        try {
            root = objectMapper.readTree(result.text());
        } catch (Exception e) {
            log.error("RAG chat extraction returned unparseable JSON: {}", result.text(), e);
            throw new IllegalStateException("Söhbətdən məlumat çıxarıla bilmədi - yenidən sınayın");
        }

        List<RagDocument> created = new ArrayList<>();
        JsonNode entries = root.path("entries");
        if (entries.isArray()) {
            for (JsonNode entry : entries) {
                String content = entry.path("content").asText(null);
                if (!StringUtils.hasText(content)) {
                    continue;
                }
                String title = entry.path("title").asText(null);
                created.add(ragService.create(tenantId,
                        new RagDocumentCreateRequest(content.trim(), StringUtils.hasText(title) ? title.trim() : null, "söhbət")));
            }
        }
        log.info("RAG chat extraction for tenant {}: {} entr{} created from a {}-turn conversation",
                tenantId, created.size(), created.size() == 1 ? "y" : "ies", history.size());
        return created;
    }

    private void requireGemini() {
        if (!geminiApiClient.isConfigured()) {
            throw new IllegalStateException("Gemini açarı qurulmayıb");
        }
    }

    private String formatTranscript(List<RagChatTurn> history) {
        StringBuilder sb = new StringBuilder();
        for (RagChatTurn turn : history) {
            sb.append("assistant".equals(turn.role()) ? "Köməkçi: " : "Sahib: ")
                    .append(turn.content()).append('\n');
        }
        return sb.toString();
    }
}
