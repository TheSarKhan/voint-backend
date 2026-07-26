package com.starsoft.voint.voice;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.starsoft.voint.llm.GeminiApiClient;
import com.starsoft.voint.llm.LlmClient;
import com.starsoft.voint.llm.LlmResult;
import com.starsoft.voint.rag.RagDocument;
import com.starsoft.voint.rag.RagDocumentRepository;
import com.starsoft.voint.rag.VectorUtils;
import com.starsoft.voint.tenant.Tenant;
import com.starsoft.voint.tenant.TenantRepository;
import com.starsoft.voint.voice.dto.ChatCompletionChunk;
import com.starsoft.voint.voice.dto.ChatCompletionRequest;
import com.starsoft.voint.voice.dto.ChatCompletionResponse;
import com.starsoft.voint.voice.dto.ChatMessage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Call-flow orchestration for the Vapi custom-LLM webhook.
 *
 * Pipeline:
 *   1. resolveTenantId() - which tenant this call belongs to (bootstrap: falls back to CES)
 *   2. detectLanguage()  - detect caller language (az/ru/en)
 *   3. ragSearch()       - pgvector semantic search over the tenant's documents
 *   4. buildPrompt()     - persona + boundaries + tenant context + RAG context + history
 *   5. callLlm()         - Gemini Flash via LlmClient (GeminiLlmClient, or MockLlmClient without a key)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceWebhookService {

    /** Bootstrap-stage fallback tenant (CES, seeded by V2__seed.sql) when no tenant_id is present. */
    private static final UUID DEFAULT_TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final int RAG_TOP_K = 4;

    /** Sentence end (incl. Azerbaijani text) followed by whitespace — one speech unit per frame. */
    private static final Pattern SENTENCE = Pattern.compile("[^.!?\\n]*[.!?\\n]+\\s*");

    private final ObjectMapper objectMapper;
    private final LlmClient llmClient;
    private final GeminiApiClient geminiApiClient;
    private final RagDocumentRepository ragDocumentRepository;
    private final TenantRepository tenantRepository;
    private final PromptLoader promptLoader;

    public ChatCompletionResponse handle(ChatCompletionRequest request) {
        return ChatCompletionResponse.assistantMessage(request.model(), generateAnswer(request));
    }

    /**
     * Same plain (non-streaming) reply as {@link #handle}, but written straight to the response
     * stream. The controller has to declare a single return type for both paths (see the note
     * there), so the JSON case travels through the streaming channel too.
     */
    public StreamingResponseBody handleAsJsonBody(ChatCompletionRequest request) {
        ChatCompletionResponse response = handle(request);
        return out -> {
            out.write(objectMapper.writeValueAsBytes(response));
            out.flush();
        };
    }

    /**
     * Streaming variant for {@code "stream": true}. Vapi drives us through an OpenAI client, which
     * on a streaming request refuses to read a plain JSON body — the agent then has nothing to say
     * and the call dies with {@code silence-timed-out}.
     *
     * Gemini is still called once for the whole answer; we then emit it sentence by sentence so
     * Vapi can start speaking the first sentence without waiting for the last.
     * TODO (later): stream from Gemini itself (streamGenerateContent) to cut time-to-first-word.
     */
    public StreamingResponseBody handleStreaming(ChatCompletionRequest request) {
        String answer = generateAnswer(request);
        String id = "chatcmpl-" + UUID.randomUUID();
        String model = request.model() != null ? request.model() : "voint-agent";

        return out -> {
            try (var writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
                boolean first = true;
                for (String piece : splitForSpeech(answer)) {
                    writeFrame(writer, ChatCompletionChunk.content(id, model, piece, first));
                    first = false;
                }
                writeFrame(writer, ChatCompletionChunk.stop(id, model));
                writer.write("data: [DONE]\n\n");
                writer.flush();
            }
        };
    }

    private void writeFrame(OutputStreamWriter writer, ChatCompletionChunk chunk) throws IOException {
        writer.write("data: " + objectMapper.writeValueAsString(chunk) + "\n\n");
        writer.flush();
    }

    /**
     * Splits on sentence endings, keeping the punctuation, so each frame is a natural speech unit.
     * Falls back to the whole string when there is nothing to split on.
     */
    private List<String> splitForSpeech(String text) {
        if (text == null || text.isBlank()) {
            return List.of("");
        }
        List<String> parts = new ArrayList<>();
        Matcher m = SENTENCE.matcher(text);
        int last = 0;
        while (m.find()) {
            parts.add(text.substring(last, m.end()));
            last = m.end();
        }
        if (last < text.length()) {
            parts.add(text.substring(last));
        }
        return parts.isEmpty() ? List.of(text) : parts;
    }

    private String generateAnswer(ChatCompletionRequest request) {
        UUID tenantId = resolveTenantId(request);
        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant == null) {
            log.error("Resolved tenantId {} does not exist in the tenants table - proceeding without tenant context", tenantId);
        }

        String userText = extractLastUserMessage(request.messages());

        String language = detectLanguage(userText);
        List<String> ragContext = ragSearch(userText, language, tenantId);
        String prompt = buildPrompt(language, ragContext, request.messages(), tenant);
        LlmResult result = callLlm(prompt, userText);

        return result.content();
    }

    /** Latest user utterance = the text Vapi transcribed from the caller. */
    private String extractLastUserMessage(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        return messages.reversed().stream()
                .filter(m -> "user".equalsIgnoreCase(m.role()))
                .map(ChatMessage::content)
                .findFirst()
                .orElse("");
    }

    /**
     * Resolves which tenant this call belongs to. BOOTSTRAP STAGE: looks for a top-level
     * {@code tenant_id} field, then {@code call.metadata.tenantId}/{@code tenant_id}; if neither
     * is present (or isn't a valid UUID), falls back to the seeded CES tenant and logs a warning.
     * TODO (later stage): real multi-tenant routing by the caller's dialed phone number.
     */
    private UUID resolveTenantId(ChatCompletionRequest request) {
        String raw = request.tenantId();
        if (raw == null || raw.isBlank()) {
            raw = extractTenantIdFromCallMetadata(request.call());
        }
        if (raw == null || raw.isBlank()) {
            log.warn("No tenant_id in webhook request (and none under call.metadata) - falling back to the "
                    + "bootstrap default CES tenant {}. TODO: real multi-tenant routing by dialed phone number.",
                    DEFAULT_TENANT_ID);
            return DEFAULT_TENANT_ID;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            log.warn("tenant_id '{}' is not a valid UUID - falling back to the bootstrap default CES tenant {}",
                    raw, DEFAULT_TENANT_ID);
            return DEFAULT_TENANT_ID;
        }
    }

    @SuppressWarnings("unchecked")
    private String extractTenantIdFromCallMetadata(Map<String, Object> call) {
        if (call == null) {
            return null;
        }
        Object metadata = call.get("metadata");
        if (!(metadata instanceof Map<?, ?> metaMap)) {
            return null;
        }
        Object value = metaMap.get("tenantId");
        if (value == null) {
            value = metaMap.get("tenant_id");
        }
        return value != null ? String.valueOf(value) : null;
    }

    /**
     * TODO: real language detection (az/ru/en) - lightweight heuristic or model call.
     * Bootstrap stub: always Azerbaijani.
     */
    private String detectLanguage(String userText) {
        return "az";
    }

    /** Embeds userText and runs a tenant-scoped pgvector cosine-distance search for the top-k chunks. */
    private List<String> ragSearch(String userText, String language, UUID tenantId) {
        if (userText == null || userText.isBlank()) {
            return List.of();
        }
        float[] embedding = geminiApiClient.embedContent(userText);
        if (embedding == null) {
            log.debug("No embedding available (Gemini not configured/unavailable) - continuing without RAG context");
            return List.of();
        }
        try {
            String vectorLiteral = VectorUtils.toPgVector(embedding);
            return ragDocumentRepository.findNearestByTenant(tenantId, vectorLiteral, RAG_TOP_K).stream()
                    .map(RagDocument::getContent)
                    .toList();
        } catch (Exception e) {
            log.error("RAG vector search failed for tenant {} - continuing without RAG context", tenantId, e);
            return List.of();
        }
    }

    /**
     * Concatenates: system-prompt.md + boundaries.md + tenant context (greeting/working hours/handoff)
     * + RAG context chunks (under "MƏLUMAT BAZASI:") + conversation history + target response language.
     */
    private String buildPrompt(String language, List<String> ragContext, List<ChatMessage> history, Tenant tenant) {
        StringBuilder sb = new StringBuilder();
        sb.append(promptLoader.getSystemPrompt()).append("\n\n");
        sb.append(promptLoader.getBoundaries()).append("\n\n");

        sb.append("TENANT MƏLUMATI:\n");
        if (tenant != null) {
            sb.append("- Şirkət: ").append(orDash(tenant.getName())).append('\n');
            sb.append("- Salamlama mətni: ").append(orDash(tenant.getGreetingText())).append('\n');
            sb.append("- İş saatları: ").append(orDash(tenant.getWorkingHours())).append('\n');
            sb.append("- İnsan operatora yönləndirmə nömrəsi (handoff_number): ").append(orDash(tenant.getHandoffNumber())).append('\n');
        } else {
            sb.append("- (tenant tapılmadı - ümumi cavab ver, lazım gələrsə insan operatora yönləndir)\n");
        }
        sb.append('\n');

        sb.append("MƏLUMAT BAZASI:\n");
        if (ragContext.isEmpty()) {
            sb.append("(bu sual üzrə məlumat bazasında uyğun məlumat tapılmadı - bunu vicdanla bildir və handoff təklif et)\n");
        } else {
            for (int i = 0; i < ragContext.size(); i++) {
                sb.append("[").append(i + 1).append("] ").append(ragContext.get(i)).append('\n');
            }
        }
        sb.append('\n');

        sb.append("SÖHBƏT TARİXÇƏSİ:\n");
        if (history != null) {
            for (ChatMessage m : history) {
                sb.append(m.role()).append(": ").append(m.content()).append('\n');
            }
        }
        sb.append('\n');

        sb.append("CAVAB DİLİ: ").append(languageLabel(language)).append(" dilində, telefon danışığına uyğun tərzdə cavab ver.\n");
        return sb.toString();
    }

    private String orDash(String value) {
        return value != null && !value.isBlank() ? value : "-";
    }

    private String languageLabel(String language) {
        return switch (language == null ? "" : language.toLowerCase()) {
            case "ru" -> "rus";
            case "en" -> "ingilis";
            default -> "Azərbaycan";
        };
    }

    private LlmResult callLlm(String prompt, String userText) {
        return llmClient.complete(prompt, userText);
    }
}
