-- ============================================================================
-- V27: Tenant Business Industry / Category Field
-- ============================================================================

ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS industry VARCHAR(32) NOT NULL DEFAULT 'RENTAL';

-- Set CES tenant industry to RENTAL (Construction Equipment Rental)
UPDATE tenants
SET industry = 'RENTAL'
WHERE id = '11111111-1111-1111-1111-111111111111';

CREATE INDEX IF NOT EXISTS idx_tenants_industry ON tenants(industry);
