package com.starsoft.voint.rag;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.starsoft.voint.common.exception.NotFoundException;
import com.starsoft.voint.llm.GeminiApiClient;
import com.starsoft.voint.rag.dto.RagBackfillResponse;
import com.starsoft.voint.rag.dto.RagDocumentCreateRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private static final int TOP_K = 4;

    private final RagDocumentRepository ragDocumentRepository;
    private final GeminiApiClient geminiApiClient;

    @Transactional
    public RagDocument create(UUID tenantId, RagDocumentCreateRequest request) {
        RagDocument doc = RagDocument.builder()
                .tenantId(tenantId)
                .content(request.content())
                .category(request.category())
                .source(request.source())
                .build();
        // saveAndFlush: the embedding is written via a separate native UPDATE below, which needs
        // the INSERT to have already happened (embedding isn't JPA-mapped, see RagDocument).
        RagDocument saved = ragDocumentRepository.saveAndFlush(doc);

        float[] embedding = geminiApiClient.embedContent(request.content());
        if (embedding != null) {
            ragDocumentRepository.updateEmbedding(saved.getId(), VectorUtils.toPgVector(embedding));
        } else {
            log.warn("Could not compute embedding for new RAG document {} (tenant {}) - it will be "
                    + "invisible to semantic search until the startup backfill picks it up", saved.getId(), tenantId);
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public List<RagDocument> list(UUID tenantId) {
        return ragDocumentRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    @Transactional
    public void delete(UUID tenantId, UUID docId) {
        RagDocument doc = ragDocumentRepository.findByIdAndTenantId(docId, tenantId)
                .orElseThrow(() -> NotFoundException.of("RagDocument", docId));
        ragDocumentRepository.delete(doc);
    }

    /** Tenant-isolated pgvector semantic search: embed the query, then cosine-distance top-k. */
    @Transactional(readOnly = true)
    public List<RagDocument> semanticSearch(UUID tenantId, String query, int topK) {
        float[] embedding = geminiApiClient.embedContent(query);
        if (embedding == null) {
            log.debug("No embedding available - semantic search returning no results for tenant {}", tenantId);
            return List.of();
        }
        int k = topK > 0 ? topK : TOP_K;
        return ragDocumentRepository.findNearestByTenant(
                tenantId, VectorUtils.toPgVector(embedding), VectorUtils.MAX_COSINE_DISTANCE, k);
    }

    /**
     * Embeds every {@code rag_documents} row still missing its {@code embedding} (seed data, or a
     * row whose embed call failed when it was created). Runs at startup via
     * {@link RagEmbeddingBackfillRunner}; also callable on demand from the admin panel so a key
     * fixed mid-day doesn't need a restart to take effect.
     */
    @Transactional
    public RagBackfillResponse backfillMissingEmbeddings() {
        List<RagDocument> pending = ragDocumentRepository.findWithNullEmbedding();
        if (pending.isEmpty()) {
            log.info("RAG embedding backfill: all rag_documents already have embeddings - nothing to do");
            return new RagBackfillResponse(0, 0, geminiApiClient.isConfigured());
        }

        if (!geminiApiClient.isConfigured()) {
            log.warn("RAG embedding backfill: {} document(s) have no embedding, but GEMINI_API_KEY is not set - "
                    + "skipping. They will remain unreachable by semantic search until the key is configured.",
                    pending.size());
            return new RagBackfillResponse(pending.size(), 0, false);
        }

        log.info("RAG embedding backfill: embedding {} document(s) with NULL embedding...", pending.size());
        int done = 0;
        for (RagDocument doc : pending) {
            float[] embedding = geminiApiClient.embedContent(doc.getContent());
            if (embedding == null) {
                log.error("RAG embedding backfill: failed to embed document {} (tenant {}) - will retry next time",
                        doc.getId(), doc.getTenantId());
                continue;
            }
            ragDocumentRepository.updateEmbedding(doc.getId(), VectorUtils.toPgVector(embedding));
            done++;
        }
        log.info("RAG embedding backfill complete: {}/{} document(s) embedded", done, pending.size());
        return new RagBackfillResponse(pending.size(), done, true);
    }
}
