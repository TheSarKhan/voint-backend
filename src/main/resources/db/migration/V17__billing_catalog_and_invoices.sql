CREATE TABLE billing_plans (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL UNIQUE,
    monthly_fee NUMERIC(12,2) NOT NULL DEFAULT 0,
    included_minutes INTEGER NOT NULL DEFAULT 0,
    overage_per_minute NUMERIC(12,4) NOT NULL DEFAULT 0,
    monthly_minute_cap INTEGER NOT NULL DEFAULT 0,
    due_days INTEGER NOT NULL DEFAULT 15,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT billing_plans_due_days_check CHECK (due_days >= 0)
);

ALTER TABLE tenants
    ADD COLUMN billing_plan_id UUID REFERENCES billing_plans(id),
    ADD COLUMN billing_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN billing_legal_name VARCHAR(200),
    ADD COLUMN billing_tax_id VARCHAR(80),
    ADD COLUMN billing_email VARCHAR(254),
    ADD COLUMN billing_due_days INTEGER;

CREATE TABLE billing_invoices (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    billing_plan_id UUID REFERENCES billing_plans(id),
    period VARCHAR(7) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    due_date DATE,
    monthly_fee NUMERIC(12,2) NOT NULL,
    included_minutes INTEGER NOT NULL,
    overage_minutes NUMERIC(12,2) NOT NULL,
    overage_per_minute NUMERIC(12,4) NOT NULL,
    usage_minutes NUMERIC(12,2) NOT NULL,
    provider_cost NUMERIC(12,2) NOT NULL,
    total_amount NUMERIC(12,2) NOT NULL,
    locked_at TIMESTAMPTZ,
    sent_at TIMESTAMPTZ,
    paid_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT billing_invoices_period_check CHECK (period ~ '^\\d{4}-\\d{2}$'),
    CONSTRAINT billing_invoices_tenant_period_unique UNIQUE (tenant_id, period)
);

CREATE INDEX billing_invoices_period_idx ON billing_invoices(period);
