package com.starsoft.voint.rag;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.starsoft.voint.common.exception.NotFoundException;
import com.starsoft.voint.llm.GeminiApiClient;
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
        return ragDocumentRepository.findNearestByTenant(tenantId, VectorUtils.toPgVector(embedding), k);
    }
}
