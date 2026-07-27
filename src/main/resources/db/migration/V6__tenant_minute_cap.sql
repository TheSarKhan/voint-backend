-- A hard monthly ceiling per tenant, separate from the billing allowance.
--
-- These are two different controls and conflating them would be a mistake:
--   * included_minutes is COMMERCIAL - talk more, pay more. It protects revenue.
--   * monthly_minute_cap is a COST control. It exists for the case where a customer's own
--     system misbehaves (a broken auto-dialler, a loop) and burns a month of credits overnight.
--     Someone willing to pay the overage should still not be able to do that unnoticed.
--
-- 0 means no ceiling, which is the default, so adding this column changes nothing for anyone
-- until an operator deliberately sets a limit.

ALTER TABLE tenants
    ADD COLUMN monthly_minute_cap INTEGER NOT NULL DEFAULT 0;

-- Enforcement happens on the first turn of a call, which is detected by asking whether this
-- Vapi call id has been seen before. Without this index that lookup scans the table on every turn.
CREATE INDEX idx_usage_events_call ON usage_events (vapi_call_id);

-- CES: a ceiling far above its real traffic (~19 minutes last month) and well above its 800
-- included minutes. High enough never to interfere, low enough to stop a runaway.
UPDATE tenants
SET monthly_minute_cap = 2000
WHERE id = '11111111-1111-1111-1111-111111111111';
