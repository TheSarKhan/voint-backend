package com.starsoft.voint.rag;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.starsoft.voint.llm.GeminiApiClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Startup job: embeds any {@code rag_documents} row still missing its {@code embedding} (e.g. the
 * CES seed data, which is inserted with NULL embeddings by {@code V2__seed.sql}). Idempotent -
 * once every row has an embedding this is a cheap no-op on subsequent restarts.
 */
@Slf4j
@Component
@Order(100)
@RequiredArgsConstructor
public class RagEmbeddingBackfillRunner implements ApplicationRunner {

    private final RagDocumentRepository ragDocumentRepository;
    private final GeminiApiClient geminiApiClient;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<RagDocument> pending = ragDocumentRepository.findWithNullEmbedding();
        if (pending.isEmpty()) {
            log.info("RAG embedding backfill: all rag_documents already have embeddings - nothing to do");
            return;
        }

        if (!geminiApiClient.isConfigured()) {
            log.warn("RAG embedding backfill: {} document(s) have no embedding, but GEMINI_API_KEY is not set - "
                    + "skipping. They will remain unreachable by semantic search until the key is configured "
                    + "and the app is restarted.", pending.size());
            return;
        }

        log.info("RAG embedding backfill: embedding {} document(s) with NULL embedding...", pending.size());
        int done = 0;
        for (RagDocument doc : pending) {
            float[] embedding = geminiApiClient.embedContent(doc.getContent());
            if (embedding == null) {
                log.error("RAG embedding backfill: failed to embed document {} (tenant {}) - will retry on next restart",
                        doc.getId(), doc.getTenantId());
                continue;
            }
            ragDocumentRepository.updateEmbedding(doc.getId(), VectorUtils.toPgVector(embedding));
            done++;
        }
        log.info("RAG embedding backfill complete: {}/{} document(s) embedded", done, pending.size());
    }
}
