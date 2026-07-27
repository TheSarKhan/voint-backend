-- Pilot requests submitted from the public landing page (voint-landing).
--
-- Deliberately NOT a tenant-scoped table: a lead is precisely someone who is not a customer yet.
-- Hanging it off tenants would mean inventing a tenant row for every form submission, spam
-- included, and every tenant-scoped query would then have to learn to skip them.

CREATE TABLE leads (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name         VARCHAR(160) NOT NULL,
    company           VARCHAR(160) NOT NULL,
    industry          VARCHAR(80),
    phone             VARCHAR(40)  NOT NULL,
    email             VARCHAR(160) NOT NULL,
    -- Free text on purpose: the form asks for a rough number and callers answer "40-50".
    daily_call_volume VARCHAR(40),
    -- Which surface produced it, so a second landing or a campaign can be told apart later
    -- without a schema change.
    source            VARCHAR(60)  NOT NULL DEFAULT 'landing',
    status            VARCHAR(20)  NOT NULL DEFAULT 'NEW',
    -- What the operator wrote after calling them back.
    note              TEXT,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ
);

-- The admin table lists newest first and filters by status; one index serves both.
CREATE INDEX idx_leads_status_created ON leads (status, created_at DESC);
CREATE INDEX idx_leads_created ON leads (created_at DESC);

-- Supports the double-submit guard, which looks up the most recent lead with the same email.
CREATE INDEX idx_leads_email ON leads (lower(email));
