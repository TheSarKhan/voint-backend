ALTER TABLE billing_plans
    ADD COLUMN max_concurrent_calls INTEGER NOT NULL DEFAULT 1,
    ADD CONSTRAINT billing_plans_max_concurrent_calls_check CHECK (max_concurrent_calls >= 1);

ALTER TABLE tenants
    ADD COLUMN max_concurrent_calls INTEGER NOT NULL DEFAULT 1,
    ADD CONSTRAINT tenants_max_concurrent_calls_check CHECK (max_concurrent_calls >= 1);
