package com.starsoft.voint.question;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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

    /** Xülasə + suallar + bütöv təmizlənmiş transkript (uzun zənglərdə bir neçə yüz söz ola bilər). */
    private static final int MAX_OUTPUT_TOKENS = 4000;

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

            3. TƏMİZLƏNMİŞ TRANSKRİPT: eyni söhbətin daha oxunaqlı yazısı — səs tanımanın (STT)
               artefaktlarını təmizlə: yarımçıq kəsilmiş sözlər (məs. "Ba●", "Al●"), iki tərəfin
               bir-birini kəsdiyi təkrarlar, mənasız/səhv tanınmış fraqmentlər. Məqsəd operatorun
               əsl söhbəti rahat oxumasıdır.

               MÜTLƏQ QAYDALAR:
               - HEÇ NƏ UYDURMA. Yalnız orijinalda GERÇƏKDƏN deyilənləri aydın şəkildə yenidən yaz.
               - Bir hissə həqiqətən anlaşılmazdırsa, təxmin ETMƏ — "[aydın deyil]" yaz və keç.
               - Mənanı, faktları, rəqəmləri DƏYİŞMƏ — yalnız aydınlaşdır, tərcümə etmə, əlavə etmə.
               - Danışan növbələrini qoru: hər sətir "Müştəri:" və ya "Agent:" ilə başlasın.
               - Salamlaşma/sağollaşma kimi adi hissələri saxla, sadəcə səs-küy artefaktlarını çıxar.
               - İSTİSNA: sənə aşağıda ŞİRKƏTİN DƏQİQ ADI verilib. Bu, təxmin deyil, bilinən
                 faktdır — transkriptdə bu adın aydın səs-tanıma təhrifi görünürsə (məs. "Tez
                 Texnika" əvəzinə "CES Texnika"), onu düzgün ada düzəlt. Başqa heç bir sözü bu
                 formada "düzəltmə" — yalnız bu bir adı, çünki onu artıq bilirik.

            Cavabı bu JSON sxemində qaytar:
            {"summary": "...", "unansweredQuestions": [{"question": "...", "context": "..."}], "cleanedTranscript": "..."}

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
    public void analyzeAsync(UUID tenantId, UUID callId, String transcript, String tenantName) {
        try {
            analyze(tenantId, callId, transcript, tenantName);
        } catch (Exception e) {
            log.error("Call analysis failed for call {} (tenant {})", callId, tenantId, e);
        }
    }

    void analyze(UUID tenantId, UUID callId, String transcript, String tenantName) {
        if (transcript == null || transcript.isBlank()) {
            writer.markFailed(callId, "transkript boşdur");
            return;
        }
        if (!geminiApiClient.isConfigured()) {
            writer.markFailed(callId, "Gemini açarı qurulmayıb");
            return;
        }

        String userMessage = StringUtils.hasText(tenantName)
                ? "ŞİRKƏTİN DƏQİQ ADI: " + tenantName + "\n\nTRANSKRİPT:\n" + transcript
                : transcript;
        GeminiApiClient.GenerationResult result =
                geminiApiClient.generateJson(SYSTEM_PROMPT, userMessage, MAX_OUTPUT_TOKENS, THINKING_BUDGET);

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
        String cleanedTranscript = trimToNull(root.path("cleanedTranscript").asText(null));
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

        writer.save(tenantId, callId, summary, cleanedTranscript, found);
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
