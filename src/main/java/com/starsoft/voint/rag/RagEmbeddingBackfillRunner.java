package com.starsoft.voint.rag;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Startup job: embeds any {@code rag_documents} row still missing its {@code embedding} (e.g. the
 * CES seed data, which is inserted with NULL embeddings by {@code V2__seed.sql}). Idempotent -
 * once every row has an embedding this is a cheap no-op on subsequent restarts.
 *
 * <p>The actual work lives in {@link RagService#backfillMissingEmbeddings()} so the admin panel
 * can trigger the same pass on demand, without waiting for a restart.
 */
@Component
@Order(100)
@RequiredArgsConstructor
public class RagEmbeddingBackfillRunner implements ApplicationRunner {

    private final RagService ragService;

    @Override
    public void run(ApplicationArguments args) {
        ragService.backfillMissingEmbeddings();
    }
}
