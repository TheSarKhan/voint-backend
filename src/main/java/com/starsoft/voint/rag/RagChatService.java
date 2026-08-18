package com.starsoft.voint.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.starsoft.voint.llm.GeminiApiClient;
import com.starsoft.voint.rag.dto.RagChatTurn;
import com.starsoft.voint.rag.dto.RagDocumentCreateRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Lets a business owner build their knowledge base by chatting instead of writing documents by
 * hand. Two Gemini calls, kept deliberately separate: one holds a natural conversation (cheap,
 * short replies), the other reads the finished transcript and turns it into clean RAG entries
 * (runs once, at the end - see {@code RagChatController} for why: batching keeps this to a single
 * extraction call instead of one per message, and gives the owner one finished list to react to
 * rather than documents appearing mid-conversation).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagChatService {

    /** A chat reply is a couple of sentences - generous headroom without inviting an essay. */
    private static final int REPLY_MAX_TOKENS = 400;
    /** A full onboarding conversation can yield a few dozen entries; each is short. */
    private static final int EXTRACT_MAX_TOKENS = 3000;
    /** Neither task benefits from deliberation - a friendly chat reply and a read-and-list
     *  extraction are both cheap to answer directly, and thinking is billed. */
    private static final int THINKING_BUDGET = 0;

    private static final String INTERVIEWER_SYSTEM_PROMPT = """
            Sən Voint platformasının bilik bazası köməkçisisən. Bir biznes sahibi ilə söhbət
            edərək onun biznesini tanıyırsan - iş saatları, xidmətlər/məhsullar, qiymətlər,
            şərtlər, ünvan, tez-tez soruşulan suallar və müştərilərin bilməli olduğu hər şey.
            Bu məlumat sonra onun müştərilərinə telefonla cavab verən AI agentin bilik bazasına
            yazılacaq.

            Qaydalar:
            - Səmimi, sadə dildə danış - rəsmi sənəd dili yox, adi söhbət kimi.
            - Bir dəfəyə YALNIZ BİR mövzu haqqında soruş, uzun sual siyahısı vermə.
            - Sahibin dediyini qısaca təsdiqlə (məs. "Anladım, B.e-Cümə 9-18."), sonra növbəti
              məntiqli sualı ver - iş saatlarından sonra məsələn xidmətlər, sonra qiymətlər və s.
            - Kifayət qədər əsas mövzu (iş saatları, xidmətlər, qiymətlər, əlaqə) toplananda de
              ki, istənilən vaxt "Bitir" düyməsini basıb yadda saxlaya bilər, ya da davam edib
              daha çox detal əlavə edə bilər.
            - Sən özün heç nəyi yadda saxlamırsan - sadəcə söhbət aparırsan, məlumat söhbətin
              sonunda ayrıca oxunub çıxarılacaq.

            Cavabların QISA olsun (2-4 cümlə) - bu yazılı çatdır, mühazirə deyil.
            """;

    private static final String EXTRACT_SYSTEM_PROMPT = """
            Sən bir söhbəti oxuyub, ordan bir biznes haqqında FAKTLARI çıxarırsan ki, bunlar
            müştərilərə telefonla cavab verən AI agentin bilik bazasına yazılsın.

            Hər fakt/qayda üçün ayrıca bir giriş yarat:
            - "title": qısa başlıq, bir neçə söz (məs. "İş saatları", "Çatdırılma şərtləri")
            - "content": tam, özündən aydın cümlə(lər) - agent bunu OLDUĞU KİMİ müştəriyə deyə
              bilməlidir, söhbətin qalan hissəsini bilmədən də başa düşülməlidir.

            Qaydalar:
            - Yalnız söhbətdə HƏQİQƏTƏN deyilən şeyi yaz - heç nə uydurma, əlavə etmə, təxmin
              etmə.
            - Salamlaşma, kiçik danışıq, aydın olmayan/natamam fikirlər - bunları çıxarma.
            - Eyni mövzudakı faktları bir girişdə birləşdir (məs. bütün iş saatları bir girişdə),
              amma fərqli mövzuları qarışdırma.
            - İstifadə oluna bilən heç bir fakt yoxdursa, boş massiv qaytar - uydurma.

            Cavabı YALNIZ bu JSON sxemində qaytar, başqa mətn əlavə etmə:
            {"entries": [{"title": "...", "content": "..."}]}

            Bütün mətn Azərbaycan dilində olsun.
            """;

    private final GeminiApiClient geminiApiClient;
    private final RagService ragService;
    private final ObjectMapper objectMapper;

    /** The interviewer's next message, given the conversation so far (last turn is the owner's). */
    public String reply(List<RagChatTurn> history) {
        requireGemini();
        String latest = history.get(history.size() - 1).content();
        String systemPrompt = INTERVIEWER_SYSTEM_PROMPT + "\n\nSÖHBƏT TARİXÇƏSİ:\n" + formatTranscript(history);
        GeminiApiClient.GenerationResult result = geminiApiClient.generateContent(systemPrompt, latest);
        return result.text();
    }

    /**
     * Reads the whole conversation and files each extracted fact as a real RAG document (via
     * {@link RagService#create}, so it goes through the exact same embedding step a hand-typed
     * document would) - "title" becomes the document's category, "content" its body.
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
