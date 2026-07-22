-- Voint bootstrap schema
-- PostgreSQL 16 + pgvector

CREATE EXTENSION IF NOT EXISTS vector;

-- ---------------------------------------------------------------------------
-- tenants: one row per business using the platform
-- ---------------------------------------------------------------------------
CREATE TABLE tenants (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    phone_number    VARCHAR(32),
    greeting_text   VARCHAR(1024),
    working_hours   VARCHAR(512),
    handoff_number  VARCHAR(32),
    language_config VARCHAR(1024),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- rag_documents: tenant knowledge base chunks with pgvector embeddings.
-- NOTE: the embedding column is intentionally NOT mapped in the JPA entity at
-- the bootstrap stage; it is written/queried via native SQL when the real RAG
-- pipeline lands. 768 dims = Gemini text-embedding output size.
-- ---------------------------------------------------------------------------
CREATE TABLE rag_documents (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id  UUID NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    content    TEXT NOT NULL,
    embedding  vector(768),
    category   VARCHAR(128),
    source     VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_rag_documents_tenant ON rag_documents (tenant_id);

-- ---------------------------------------------------------------------------
-- calls: call journal
-- ---------------------------------------------------------------------------
CREATE TABLE calls (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    caller_number     VARCHAR(32),
    language_detected VARCHAR(16),
    status            VARCHAR(16) NOT NULL DEFAULT 'ONGOING'
                      CHECK (status IN ('ONGOING', 'RESOLVED', 'HANDOFF')),
    duration_seconds  INTEGER,
    started_at        TIMESTAMPTZ,
    ended_at          TIMESTAMPTZ
);

CREATE INDEX idx_calls_tenant ON calls (tenant_id);
CREATE INDEX idx_calls_tenant_status ON calls (tenant_id, status);

-- ---------------------------------------------------------------------------
-- call_transcripts: full transcript + AI summary per call
-- ---------------------------------------------------------------------------
CREATE TABLE call_transcripts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    call_id         UUID NOT NULL REFERENCES calls (id) ON DELETE CASCADE,
    full_transcript TEXT,
    ai_summary      TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_call_transcripts_call ON call_transcripts (call_id);

-- ---------------------------------------------------------------------------
-- customers: CRM cards, repeat callers recognized by phone number
-- ---------------------------------------------------------------------------
CREATE TABLE customers (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    phone_number  VARCHAR(32) NOT NULL,
    name          VARCHAR(255),
    notes         TEXT,
    first_seen_at TIMESTAMPTZ,
    last_seen_at  TIMESTAMPTZ,
    CONSTRAINT uq_customers_tenant_phone UNIQUE (tenant_id, phone_number)
);

CREATE INDEX idx_customers_tenant ON customers (tenant_id);

-- ---------------------------------------------------------------------------
-- reservation_requests: collected by the agent, pending human confirmation
-- ---------------------------------------------------------------------------
CREATE TABLE reservation_requests (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    call_id           UUID REFERENCES calls (id) ON DELETE SET NULL,
    customer_name     VARCHAR(255),
    requested_service VARCHAR(512),
    requested_date    VARCHAR(255),
    status            VARCHAR(16) NOT NULL DEFAULT 'PENDING'
                      CHECK (status IN ('PENDING', 'CONFIRMED', 'REJECTED')),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_reservation_requests_tenant ON reservation_requests (tenant_id);

-- ---------------------------------------------------------------------------
-- panel_users: admin panel users (JWT auth)
-- ---------------------------------------------------------------------------
CREATE TABLE panel_users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(64) NOT NULL DEFAULT 'ADMIN',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- NOTE: analytics are computed at runtime from the calls table.
-- There is intentionally NO analytics snapshot table at this stage.
