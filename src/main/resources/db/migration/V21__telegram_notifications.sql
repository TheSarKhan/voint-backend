-- A tenant can link more than one Telegram chat (e.g. owner + a staff member, or a group),
-- so this is a one-to-many table rather than a single column on tenants.
CREATE TABLE tenant_telegram_chats (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    chat_id BIGINT NOT NULL,
    label TEXT,
    linked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_tenant_telegram_chats_tenant_chat UNIQUE (tenant_id, chat_id)
);

CREATE INDEX idx_tenant_telegram_chats_tenant_id ON tenant_telegram_chats(tenant_id);
