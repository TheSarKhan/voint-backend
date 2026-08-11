-- Custom knowledge-base headings a tenant adds itself, beyond the built-in topic list the
-- panel ships with (pricing, working-hours, ...). Independent of rag_documents on purpose:
-- a heading can exist before any document is filed under it, and removing a heading must
-- never delete the documents that already used it.
CREATE TABLE rag_categories (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    name VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX rag_categories_tenant_name_uq ON rag_categories (tenant_id, lower(name));

-- Lets an owner pause a knowledge entry (seasonal offer, a wrong price waiting to be checked)
-- without losing it - the agent stops using it, but delete-and-retype isn't required to bring
-- it back.
ALTER TABLE rag_documents ADD COLUMN active BOOLEAN NOT NULL DEFAULT true;
