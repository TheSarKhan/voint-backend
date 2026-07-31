package com.starsoft.voint.question;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.starsoft.voint.llm.GeminiApiClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Zəng bitəndən sonra transkripti oxuyub iki şey çıxarır: operator üçün xülasə, və agentin
 * cavablaya bilmədiyi suallar.
 *
 * <p>Nə üçün zəngdən SONRA, zəng əsnasında yox: RAG axtarışı həmişə ən yaxın 4 sənədi qaytarır,
 * məsafə həddi yoxdur — yəni "uyğun sənəd tapılmadı" vəziyyəti kodda heç vaxt yaranmır. Zəng
 * əsnasında həddi təxmin etmək olardı, amma yaxın sənəd tapılıb yenə də cavab verilməyən hal
 * ondan da qaçardı. Bitmiş söhbətə baxanda isə sual verildi/verilmədi və cavablandı/cavablanmadı
 * birbaşa görünür.
 *
 * <p>Nə üçün {@code @Async}: bu Vapi-nin webhook cavabını gözlədə bilməz. Vapi tez 200 gözləyir,
 * Gemini çağırışı isə saniyələr çəkir.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CallAnalysisService {

    /** Xülasə + bir neçə sual üçün kifayətdir; danışıq büdcəsi (400) burada çox azdır. */
    private static final int MAX_OUTPUT_TOKENS = 1200;

    /**
     * Bu iş sadə oxu-çıxar işidir, mühakimə zənciri deyil — və telefonda olmadığımız üçün
     * gecikmə problem deyilsə də, düşünmə pulla ölçülür. 0 saxlanılır, keyfiyyət pisləşsə qaldırılır.
     */
    private static final int THINKING_BUDGET = 0;

    private static final String SYSTEM_PROMPT = """
            Sən zəng mərkəzi keyfiyyət analitikisən. Sənə bir AI səs agentinin müştəri ilə telefon
            danışığının transkripti verilir. İki şey çıxar:

            1. XÜLASƏ: söhbətin 2-3 cümləlik xülasəsi. Operator bunu oxuyub nə baş verdiyini
               anlamalıdır. Əgər agent nəyisə cavablaya bilmədisə, xülasədə bunu AÇIQ yaz —
               operator zəngin niyə yarımçıq qaldığını bilməlidir.

            2. CAVABSIZ SUALLAR: müştərinin verdiyi, agentin isə cavablaya BİLMƏDİYİ suallar.
               Bura yalnız bunlar daxildir:
                 - agent "bilmirəm", "məlumatım yoxdur", "operatorla əlaqələndirim" dedisə
                 - agent mövzunu dəyişdisə və ya sualı cavabsız buraxdısa
               Bura DAXİL DEYİL:
                 - agentin düzgün cavabladığı suallar
                 - qiymət razılaşması, sifariş qeydi kimi onsuz da insan tələb edən əməliyyatlar
                 - salamlaşma, sağollaşma, təkrar soruşma

               Hər sual üçün:
                 - "question": aydın, tam sual cümləsi. Transkriptdəki yarımçıq ifadəni düzəlt
                   ("bəs neçəyədir o" → "Kranın günlük kirayə qiyməti neçədir?").
                 - "context": bir cümlə — söhbətin hansı məqamında soruşulub. Operator kontekstsiz
                   sualı görəndə nədən danışıldığını anlamır.

            Cavabsız sual yoxdursa boş massiv qaytar. Sual UYDURMA — yalnız transkriptdə həqiqətən
            verilmiş sualları yaz.

            Cavabı bu JSON sxemində qaytar:
            {"summary": "...", "unansweredQuestions": [{"question": "...", "context": "..."}]}

            Bütün mətn Azərbaycan dilində olsun.
            """;

    private final GeminiApiClient geminiApiClient;
    private final ObjectMapper objectMapper;
    private final CallAnalysisWriter writer;

    /** Təhlilin nəticəsindəki bir sual. */
    public record FoundQuestion(String question, String context) {
    }

    /**
     * Bir zəngi təhlil edir. Arxa fonda işləyir və HEÇ VAXT exception atmır: bu, zəngin özündən
     * sonrakı əlavə işdir, uğursuzluğu zəng qeydini və ya webhook cavabını poza bilməz.
     */
    @Async("callAnalysisExecutor")
    public void analyzeAsync(UUID tenantId, UUID callId, String transcript) {
        try {
            analyze(tenantId, callId, transcript);
        } catch (Exception e) {
            log.error("Call analysis failed for call {} (tenant {})", callId, tenantId, e);
        }
    }

    void analyze(UUID tenantId, UUID callId, String transcript) {
        if (transcript == null || transcript.isBlank()) {
            writer.markFailed(callId, "transkript boşdur");
            return;
        }
        if (!geminiApiClient.isConfigured()) {
            writer.markFailed(callId, "Gemini açarı qurulmayıb");
            return;
        }

        GeminiApiClient.GenerationResult result =
                geminiApiClient.generateJson(SYSTEM_PROMPT, transcript, MAX_OUTPUT_TOKENS, THINKING_BUDGET);

        JsonNode root;
        try {
            root = objectMapper.readTree(result.text());
        } catch (Exception e) {
            // Modelin qaytardığını olduğu kimi loga yazırıq: sxem pozulanda promptu düzəltmək üçün
            // əsl mətn lazımdır, "parse xətası" sətri isə heç nə demir.
            log.error("Call analysis returned unparseable JSON for call {}: {}", callId, result.text(), e);
            writer.markFailed(callId, "JSON oxunmadı");
            return;
        }

        String summary = root.path("summary").asText(null);
        List<FoundQuestion> found = new ArrayList<>();
        JsonNode questions = root.path("unansweredQuestions");
        if (questions.isArray()) {
            for (JsonNode q : questions) {
                String question = q.path("question").asText(null);
                if (question == null || question.isBlank()) {
                    continue;
                }
                found.add(new FoundQuestion(question.trim(), trimToNull(q.path("context").asText(null))));
            }
        }

        writer.save(tenantId, callId, summary, found);
        log.info("Analysed call {} (tenant {}): {} unanswered question(s), {} tokens",
                callId, tenantId, found.size(), result.promptTokens() + result.completionTokens());
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
