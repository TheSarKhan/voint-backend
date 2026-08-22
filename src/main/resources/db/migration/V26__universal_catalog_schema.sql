-- ============================================================================
-- V26: Universal Catalog & Services Schema (Industry-Agnostic)
-- ============================================================================

ALTER TABLE catalog_items
    ADD COLUMN IF NOT EXISTS price NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS item_type VARCHAR(32) NOT NULL DEFAULT 'SERVICE',
    ADD COLUMN IF NOT EXISTS duration_minutes INTEGER,
    ADD COLUMN IF NOT EXISTS currency VARCHAR(8) NOT NULL DEFAULT 'AZN';

-- Backfill price from price_daily for existing rental records
UPDATE catalog_items
SET price = COALESCE(price_daily, price_hourly, price_monthly, 0)
WHERE price IS NULL;

-- If item has rental prices, tag it as RENTAL
UPDATE catalog_items
SET item_type = 'RENTAL'
WHERE price_daily IS NOT NULL OR price_monthly IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_catalog_items_type ON catalog_items(tenant_id, item_type);
