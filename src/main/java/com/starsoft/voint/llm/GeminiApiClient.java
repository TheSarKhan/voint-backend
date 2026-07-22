package com.starsoft.voint.llm;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Thin synchronous client for the Gemini API (generateContent + embedContent), used both by
 * {@link GeminiLlmClient} (chat completions) and by the RAG pipeline (embeddings).
 *
 * Model names are NOT hardcoded: at startup (if an API key is configured) this class calls
 * the Gemini {@code ListModels} endpoint and picks a "flash" generation model and an
 * "embedding" model, unless pinned via {@code voint.gemini.model} / {@code voint.gemini.embedding-model}.
 *
 * Both {@link #generateContent} and {@link #embedContent} are designed to never let a network
 * or API failure propagate as a hard error into a voice call: {@code embedContent} returns
 * {@code null} on any failure (callers degrade to "no RAG context"), while
 * {@code generateContent} throws {@link GeminiApiException} which {@link GeminiLlmClient}
 * turns into a safe fallback answer.
 */
@Slf4j
@Component
public class GeminiApiClient {

    private static final String BASE_URL = "https://generativelanguage.googleapis.com";
    private static final int EMBEDDING_OUTPUT_DIMENSIONS = 768;

    private final String apiKey;
    private final String pinnedChatModel;
    private final String pinnedEmbeddingModel;
    private final RestClient restClient;

    private volatile boolean modelsDiscovered = false;
    private volatile String chatModel;
    private volatile String embeddingModel;

    public GeminiApiClient(@Value("${voint.gemini.api-key:}") String apiKey,
                            @Value("${voint.gemini.model:}") String pinnedChatModel,
                            @Value("${voint.gemini.embedding-model:}") String pinnedEmbeddingModel) {
        this.apiKey = apiKey;
        this.pinnedChatModel = pinnedChatModel;
        this.pinnedEmbeddingModel = pinnedEmbeddingModel;
        this.restClient = RestClient.builder().baseUrl(BASE_URL).build();
    }

    public boolean isConfigured() {
        return StringUtils.hasText(apiKey);
    }

    @PostConstruct
    void init() {
        if (!isConfigured()) {
            log.info("GEMINI_API_KEY not set - Gemini model discovery skipped (LlmClient will fall back to MockLlmClient)");
            return;
        }
        ensureModelsDiscovered();
    }

    /**
     * Calls Gemini {@code generateContent}. Throws {@link GeminiApiException} on any failure -
     * callers (voice pipeline) must catch and fall back to a safe canned response.
     */
    public GenerationResult generateContent(String systemPrompt, String userMessage) {
        if (!isConfigured()) {
            throw new GeminiApiException("Gemini API key is not configured");
        }
        ensureModelsDiscovered();
        if (chatModel == null) {
            throw new GeminiApiException("No suitable Gemini 'flash' generateContent model was discovered");
        }
        try {
            GenerateContentRequest body = new GenerateContentRequest(
                    List.of(new Content("user", List.of(new Part(userMessage)))),
                    StringUtils.hasText(systemPrompt) ? new SystemInstruction(List.of(new Part(systemPrompt))) : null);

            GenerateContentResponse resp = restClient.post()
                    .uri(b -> b.path("/v1beta/" + chatModel + ":generateContent").queryParam("key", apiKey).build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(GenerateContentResponse.class);

            if (resp == null || resp.candidates() == null || resp.candidates().isEmpty()) {
                throw new GeminiApiException("Gemini generateContent returned no candidates (possibly blocked by safety filters)");
            }
            GenerateContentCandidate candidate = resp.candidates().get(0);
            if (candidate.content() == null || candidate.content().parts() == null || candidate.content().parts().isEmpty()) {
                throw new GeminiApiException("Gemini generateContent candidate had no text parts (finishReason="
                        + candidate.finishReason() + ")");
            }
            String text = candidate.content().parts().stream()
                    .map(Part::text)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining());

            int promptTokens = resp.usageMetadata() != null && resp.usageMetadata().promptTokenCount() != null
                    ? resp.usageMetadata().promptTokenCount() : 0;
            int completionTokens = resp.usageMetadata() != null && resp.usageMetadata().candidatesTokenCount() != null
                    ? resp.usageMetadata().candidatesTokenCount() : 0;

            return new GenerationResult(text, promptTokens, completionTokens);
        } catch (RestClientException e) {
            throw new GeminiApiException("Gemini generateContent call failed: " + e.getMessage(), e);
        }
    }

    /**
     * Calls Gemini {@code embedContent}, requesting {@value #EMBEDDING_OUTPUT_DIMENSIONS} output
     * dimensions to match the {@code vector(768)} DB column. Never throws - returns {@code null}
     * when the API key is missing, no embedding model was discovered, or the call fails, so RAG
     * search/backfill can degrade gracefully instead of hard-failing.
     */
    public float[] embedContent(String text) {
        if (!isConfigured()) {
            log.debug("Gemini API key not configured - cannot compute embedding");
            return null;
        }
        ensureModelsDiscovered();
        if (embeddingModel == null) {
            log.warn("No suitable Gemini embedding model was discovered - cannot compute embedding");
            return null;
        }
        try {
            EmbedContentRequest body = new EmbedContentRequest(
                    new EmbedContentInner(List.of(new Part(text))), EMBEDDING_OUTPUT_DIMENSIONS);

            EmbedContentResponse resp = restClient.post()
                    .uri(b -> b.path("/v1beta/" + embeddingModel + ":embedContent").queryParam("key", apiKey).build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(EmbedContentResponse.class);

            if (resp == null || resp.embedding() == null || resp.embedding().values() == null) {
                log.error("Gemini embedContent returned no embedding values");
                return null;
            }
            List<Double> values = resp.embedding().values();
            if (values.size() != EMBEDDING_OUTPUT_DIMENSIONS) {
                log.warn("Gemini embedding model {} returned {} dimensions, expected {} - this will NOT fit the "
                        + "vector(768) column", embeddingModel, values.size(), EMBEDDING_OUTPUT_DIMENSIONS);
            }
            float[] out = new float[values.size()];
            for (int i = 0; i < values.size(); i++) {
                out[i] = values.get(i).floatValue();
            }
            return out;
        } catch (RestClientException e) {
            log.error("Gemini embedContent call failed", e);
            return null;
        }
    }

    private synchronized void ensureModelsDiscovered() {
        if (modelsDiscovered) {
            return;
        }
        try {
            chatModel = StringUtils.hasText(pinnedChatModel) ? normalize(pinnedChatModel) : null;
            embeddingModel = StringUtils.hasText(pinnedEmbeddingModel) ? normalize(pinnedEmbeddingModel) : null;

            if (chatModel == null || embeddingModel == null) {
                ListModelsResponse resp = restClient.get()
                        .uri(b -> b.path("/v1beta/models").queryParam("key", apiKey).build())
                        .retrieve()
                        .body(ListModelsResponse.class);

                List<GeminiModel> models = resp != null && resp.models() != null ? resp.models() : List.of();

                if (chatModel == null) {
                    chatModel = models.stream()
                            .filter(m -> m.name() != null
                                    && m.name().toLowerCase().contains("flash")
                                    && m.supportedGenerationMethods() != null
                                    && m.supportedGenerationMethods().contains("generateContent"))
                            .map(GeminiModel::name)
                            .findFirst()
                            .orElse(null);
                }
                if (embeddingModel == null) {
                    embeddingModel = models.stream()
                            .filter(m -> m.name() != null
                                    && m.name().toLowerCase().contains("embedding")
                                    && m.supportedGenerationMethods() != null
                                    && m.supportedGenerationMethods().contains("embedContent"))
                            .map(GeminiModel::name)
                            .findFirst()
                            .orElse(null);
                }
            }

            if (chatModel != null) {
                log.info("Gemini chat (flash) model selected: {}", chatModel);
            } else {
                log.error("Gemini: no suitable 'flash' generateContent model was discovered via ListModels");
            }
            if (embeddingModel != null) {
                log.info("Gemini embedding model selected: {}", embeddingModel);
            } else {
                log.error("Gemini: no suitable embedContent-capable embedding model was discovered via ListModels");
            }
        } catch (RestClientException e) {
            log.error("Gemini ListModels discovery failed - chat/embedding calls will use safe fallbacks", e);
        } finally {
            modelsDiscovered = true;
        }
    }

    private static String normalize(String modelName) {
        return modelName.startsWith("models/") ? modelName : "models/" + modelName;
    }

    /** Text + token usage extracted from a generateContent response. */
    public record GenerationResult(String text, int promptTokens, int completionTokens) {
    }

    // ---- Gemini REST DTOs (only the fields we need; everything else is ignored) ----

    private record Part(String text) {
    }

    private record Content(String role, List<Part> parts) {
    }

    private record SystemInstruction(List<Part> parts) {
    }

    private record GenerateContentRequest(List<Content> contents, SystemInstruction systemInstruction) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GenerateContentCandidateContent(List<Part> parts, String role) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GenerateContentCandidate(GenerateContentCandidateContent content, String finishReason) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record UsageMetadata(Integer promptTokenCount, Integer candidatesTokenCount, Integer totalTokenCount) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GenerateContentResponse(List<GenerateContentCandidate> candidates, UsageMetadata usageMetadata) {
    }

    private record EmbedContentInner(List<Part> parts) {
    }

    private record EmbedContentRequest(EmbedContentInner content, Integer outputDimensionality) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EmbedContentEmbedding(List<Double> values) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EmbedContentResponse(EmbedContentEmbedding embedding) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GeminiModel(String name, List<String> supportedGenerationMethods) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ListModelsResponse(List<GeminiModel> models) {
    }
}
