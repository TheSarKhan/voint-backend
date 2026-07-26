-- Per-tenant usage metering, so a bill can be measured instead of estimated.
--
-- Two sources, deliberately not merged:
--   * calls.duration_seconds  -> billable minutes (already recorded from Vapi's end-of-call-report)
--   * usage_events            -> AI consumption per conversation turn
-- Keeping minutes in "calls" avoids double bookkeeping: there is exactly one place that knows
-- how long a call was.

CREATE TABLE usage_events (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    -- Vapi's own call id when the webhook carried one, so a turn can be traced back to a call.
    vapi_call_id      VARCHAR(128),
    occurred_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    prompt_tokens     INTEGER NOT NULL DEFAULT 0,
    completion_tokens INTEGER NOT NULL DEFAULT 0,
    -- Characters handed to the TTS engine. This is not an estimate: it is exactly the text we
    -- stream back to Vapi, which is exactly what ElevenLabs charges for.
    tts_characters    INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_usage_events_tenant_time ON usage_events (tenant_id, occurred_at);

-- Monthly minute rollups filter on started_at; idx_calls_tenant alone cannot serve that.
CREATE INDEX idx_calls_tenant_started ON calls (tenant_id, started_at);

-- What the tenant is charged. Amounts are in AZN; provider costs stay in USD in config and are
-- converted at read time (see BillingProperties.usd-to-azn).
ALTER TABLE tenants
    ADD COLUMN monthly_fee        NUMERIC(12, 2) NOT NULL DEFAULT 0,
    ADD COLUMN included_minutes   INTEGER        NOT NULL DEFAULT 0,
    ADD COLUMN overage_per_minute NUMERIC(12, 4) NOT NULL DEFAULT 0;

-- CES pilot plan: 350 AZN/month covering 800 minutes, 0.70 AZN per minute beyond that.
UPDATE tenants
SET monthly_fee        = 350,
    included_minutes   = 800,
    overage_per_minute = 0.70
WHERE id = '11111111-1111-1111-1111-111111111111';
