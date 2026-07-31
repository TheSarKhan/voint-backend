package com.starsoft.voint.question;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.starsoft.voint.auth.AuthenticatedUser;
import com.starsoft.voint.common.exception.NotFoundException;
import com.starsoft.voint.llm.GeminiApiClient;
import com.starsoft.voint.question.dto.AnswerQuestionRequest;
import com.starsoft.voint.question.dto.DraftAnswerResponse;
import com.starsoft.voint.rag.RagDocument;
import com.starsoft.voint.rag.RagDocumentRepository;
import com.starsoft.voint.rag.RagService;
import com.starsoft.voint.rag.dto.RagDocumentCreateRequest;
import com.starsoft.voint.tenant.Tenant;
import com.starsoft.voint.tenant.TenantRepository;
import com.starsoft.voint.auth.PanelUserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Cavabsız sualların oxunması, cavablanması və bağlanması. */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnansweredQuestionService {

    /** Qaralama bir-iki abzasdır; danışıq büdcəsindən böyük, təhlil büdcəsindən kiçik. */
    private static final int DRAFT_MAX_TOKENS = 800;

    /**
     * Qaralamanın söykənə biləcəyi sənəd sayı. Hamısını göndərmək prompt-u şişirdir və modelin
     * diqqətini yayındırır; boş bilik bazası olan müəssisədə isə onsuz da göndəriləsi bir şey yoxdur.
     */
    private static final int DRAFT_CONTEXT_DOCS = 12;

    private static final String DRAFT_SYSTEM_PROMPT = """
            Sən bir müəssisənin bilik bazasını hazırlayan köməkçisən. Müştəri telefonla bir sual
            verib, AI agent cavablaya bilməyib. Sənin işin bu suala BİLİK BAZASINA YAZILACAQ
            cavab qaralaması hazırlamaqdır.

            Qaydalar — sıra ilə:
            1. ƏVVƏLCƏ mövcud bilik bazasına və müəssisə məlumatına bax. Cavab orada varsa və ya
               oradan çıxarıla bilirsə, cavabı MƏHZ ondan qur. Bu, ən doğru mənbədir.
            2. Orada yoxdursa, həmin məlumatlara ZİDD OLMAYAN, sahə üçün məntiqli ümumi cavab təklif et.
            3. Konkret rəqəm, qiymət, müddət, nömrə və ya şərt BİLMİRSƏNSƏ — UYDURMA. Mətndə həmin
               yeri [...] kimi boş saxla və onu "missingFacts" siyahısına yaz. Uydurulmuş bir qiymət
               bilik bazasına düşsə, bundan sonra real müştərilərə telefonda deyiləcək.

            Cavab telefonda səsləndiriləcək qədər sadə və qısa olsun — 1-3 cümlə. Marketinq dili yox.

            Cavabı bu JSON sxemində qaytar:
            {"answer": "...", "usedKnowledge": ["..."], "missingFacts": ["..."]}

            "usedKnowledge": qaralamanı qurarkən istifadə etdiyin mövcud bilik bazası parçalarının
            qısa adları. Heç nə istifadə etmədinsə boş massiv.

            Bütün mətn Azərbaycan dilində olsun.
            """;

    private final UnansweredQuestionRepository questionRepository;
    private final RagDocumentRepository ragDocumentRepository;
    private final RagService ragService;
    private final TenantRepository tenantRepository;
    private final PanelUserRepository panelUserRepository;
    private final GeminiApiClient geminiApiClient;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<UnansweredQuestion> list(UUID tenantId, QuestionStatus status) {
        return status == null
                ? questionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)
                : questionRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, status);
    }

    @Transactional(readOnly = true)
    public List<UnansweredQuestion> listByCall(UUID callId) {
        return questionRepository.findByCallIdOrderByCreatedAtAsc(callId);
    }

    /** Zəng siyahısındakı işarələmə üçün: callId -> açıq sual sayı. */
    @Transactional(readOnly = true)
    public Map<UUID, Long> openCountByCall(UUID tenantId) {
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : questionRepository.countOpenByCall(tenantId)) {
            counts.put((UUID) row[0], (Long) row[1]);
        }
        return counts;
    }

    @Transactional(readOnly = true)
    public UnansweredQuestion get(UUID tenantId, UUID questionId) {
        return questionRepository.findByIdAndTenantId(questionId, tenantId)
                .orElseThrow(() -> NotFoundException.of("UnansweredQuestion", questionId));
    }

    /**
     * Cavabı bilik bazasına yazır və sualı bağlayır.
     *
     * <p>Sənəd {@link RagService} vasitəsilə yaradılır, birbaşa repository ilə yox: embedding məhz
     * orada hesablanır və onsuz sənəd semantik axtarışda GÖRÜNMÜR — yəni cavab yazılar, amma
     * növbəti zəngdə yenə tapılmazdı.
     */
    @Transactional
    public UnansweredQuestion answer(UUID tenantId, UUID questionId, AnswerQuestionRequest request) {
        UnansweredQuestion question = get(tenantId, questionId);

        RagDocument document = ragService.create(tenantId, new RagDocumentCreateRequest(
                request.content(),
                StringUtils.hasText(request.category()) ? request.category() : null,
                StringUtils.hasText(request.source()) ? request.source() : "Cavabsız sualdan"));

        question.setRagDocumentId(document.getId());
        question.setStatus(QuestionStatus.ANSWERED);
        question.setResolvedAt(Instant.now());
        question.setResolvedBy(currentUserId());
        return questionRepository.save(question);
    }

    /** "Buna cavab lazım deyil" — sual siyahıdan çıxır, amma silinmir (təkrarlanırsa görünsün). */
    @Transactional
    public UnansweredQuestion dismiss(UUID tenantId, UUID questionId) {
        UnansweredQuestion question = get(tenantId, questionId);
        question.setStatus(QuestionStatus.DISMISSED);
        question.setResolvedAt(Instant.now());
        question.setResolvedBy(currentUserId());
        return questionRepository.save(question);
    }

    /** AI qaralaması. Heç nə saxlanılmır — operator təsdiqləyənə qədər bu sadəcə təklifdir. */
    @Transactional(readOnly = true)
    public DraftAnswerResponse draft(UUID tenantId, UUID questionId) {
        UnansweredQuestion question = get(tenantId, questionId);
        if (!geminiApiClient.isConfigured()) {
            throw new IllegalStateException("Gemini açarı qurulmayıb - AI qaralama hazırlana bilmir");
        }

        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        List<RagDocument> knowledge = ragDocumentRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);

        String userMessage = buildDraftInput(question, tenant, knowledge);
        GeminiApiClient.GenerationResult result =
                geminiApiClient.generateJson(DRAFT_SYSTEM_PROMPT, userMessage, DRAFT_MAX_TOKENS, 0);

        try {
            JsonNode root = objectMapper.readTree(result.text());
            String answer = root.path("answer").asText("");
            return new DraftAnswerResponse(answer, textList(root.path("usedKnowledge")),
                    textList(root.path("missingFacts")));
        } catch (Exception e) {
            log.error("Draft answer returned unparseable JSON for question {}: {}", questionId, result.text(), e);
            // Modelin mətni oxunmadısa da əlimizdə nəsə var - operatora boş ekran göstərməkdənsə
            // xam mətni verib redaktə etməyə imkan vermək daha faydalıdır.
            return new DraftAnswerResponse(result.text(), List.of(), List.of());
        }
    }

    private String buildDraftInput(UnansweredQuestion question, Tenant tenant, List<RagDocument> knowledge) {
        StringBuilder sb = new StringBuilder();
        sb.append("MÜƏSSİSƏ MƏLUMATI:\n");
        if (tenant != null) {
            sb.append("- Şirkət: ").append(orDash(tenant.getName())).append('\n');
            sb.append("- İş saatları: ").append(orDash(tenant.getWorkingHours())).append('\n');
            sb.append("- Salamlama mətni: ").append(orDash(tenant.getGreetingText())).append('\n');
        } else {
            sb.append("- (tapılmadı)\n");
        }

        sb.append("\nMÖVCUD BİLİK BAZASI:\n");
        if (knowledge.isEmpty()) {
            sb.append("(boşdur - bu müəssisənin hələ heç bir sənədi yoxdur)\n");
        } else {
            int limit = Math.min(knowledge.size(), DRAFT_CONTEXT_DOCS);
            for (int i = 0; i < limit; i++) {
                RagDocument d = knowledge.get(i);
                sb.append("[").append(i + 1).append("] ");
                if (StringUtils.hasText(d.getCategory())) {
                    sb.append("(").append(d.getCategory()).append(") ");
                }
                sb.append(d.getContent()).append('\n');
            }
            if (knowledge.size() > limit) {
                sb.append("(... daha ").append(knowledge.size() - limit).append(" sənəd)\n");
            }
        }

        sb.append("\nCAVABLANMAMIŞ SUAL:\n").append(question.getQuestion()).append('\n');
        if (StringUtils.hasText(question.getContext())) {
            sb.append("\nSUALIN KONTEKSTİ:\n").append(question.getContext()).append('\n');
        }
        return sb.toString();
    }

    private List<String> textList(JsonNode node) {
        List<String> out = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                String value = item.asText(null);
                if (value != null && !value.isBlank()) {
                    out.add(value.trim());
                }
            }
        }
        return out;
    }

    private String orDash(String value) {
        return StringUtils.hasText(value) ? value : "-";
    }

    /**
     * Kimin bağladığı. JWT-də istifadəçi id-si yoxdur (yalnız e-poçt), ona görə baxılır — tapılmasa
     * null qalır: qeydi itirmək əməliyyatı dayandırmaqdan yaxşıdır.
     */
    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthenticatedUser user)) {
            return null;
        }
        return panelUserRepository.findByEmailIgnoreCase(user.email())
                .map(u -> u.getId())
                .orElse(null);
    }
}
