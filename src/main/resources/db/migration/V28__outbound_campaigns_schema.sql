-- ============================================================================
-- V28: Outbound Calling Campaigns Subsystem
-- ============================================================================

CREATE TABLE IF NOT EXISTS outbound_campaigns (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    campaign_type VARCHAR(64) NOT NULL DEFAULT 'SALES_OUTBOUND',
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    agent_prompt TEXT,
    greeting_text TEXT,
    calling_hours_start VARCHAR(8) NOT NULL DEFAULT '10:00',
    calling_hours_end VARCHAR(8) NOT NULL DEFAULT '19:00',
    max_retries INT NOT NULL DEFAULT 2,
    retry_interval_minutes INT NOT NULL DEFAULT 60,
    concurrency_limit INT NOT NULL DEFAULT 1,
    total_contacts INT NOT NULL DEFAULT 0,
    contacted_count INT NOT NULL DEFAULT 0,
    successful_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_outbound_campaigns_tenant ON outbound_campaigns(tenant_id);
CREATE INDEX IF NOT EXISTS idx_outbound_campaigns_status ON outbound_campaigns(status);

CREATE TABLE IF NOT EXISTS outbound_contacts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id UUID NOT NULL REFERENCES outbound_campaigns(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    phone_number VARCHAR(32) NOT NULL,
    customer_name VARCHAR(255),
    custom_data TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    call_outcome VARCHAR(64),
    retry_count INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ,
    last_attempt_at TIMESTAMPTZ,
    call_id UUID,
    summary TEXT,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_outbound_contacts_campaign ON outbound_contacts(campaign_id);
CREATE INDEX IF NOT EXISTS idx_outbound_contacts_tenant ON outbound_contacts(tenant_id);
CREATE INDEX IF NOT EXISTS idx_outbound_contacts_status ON outbound_contacts(status);
CREATE INDEX IF NOT EXISTS idx_outbound_contacts_phone ON outbound_contacts(phone_number);

-- Grant CAMPAIGN permissions to Sahib (Owner) and Platform Admin
INSERT INTO role_permissions (role_id, resource, action)
SELECT r.id, 'CAMPAIGN', a.action
FROM roles r
CROSS JOIN (VALUES ('READ'), ('CREATE'), ('UPDATE'), ('DELETE')) AS a(action)
WHERE r.name = 'Sahib' OR r.id = 'a0000000-0000-0000-0000-000000000001'
ON CONFLICT DO NOTHING;
