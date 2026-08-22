-- ============================================================================
-- V24: Product & Price Catalog, Tenant API Keys and Webhooks
-- ============================================================================

-- 1. Catalog Items Table
CREATE TABLE catalog_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    sku             VARCHAR(64),
    name            VARCHAR(255) NOT NULL,
    category        VARCHAR(128),
    price_daily     NUMERIC(12, 2),
    price_monthly   NUMERIC(12, 2),
    price_hourly    NUMERIC(12, 2),
    deposit         NUMERIC(12, 2),
    unit            VARCHAR(32) DEFAULT 'gün',
    in_stock        BOOLEAN NOT NULL DEFAULT true,
    stock_quantity  INTEGER DEFAULT 1,
    specs           TEXT,
    description     TEXT,
    active          BOOLEAN NOT NULL DEFAULT true,
    embedding       vector(768),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_catalog_items_tenant ON catalog_items(tenant_id);
CREATE INDEX idx_catalog_items_tenant_active ON catalog_items(tenant_id, active);
CREATE INDEX idx_catalog_items_sku ON catalog_items(tenant_id, sku);

-- 2. Tenant API Keys (for 1C, ERP, website integrations)
CREATE TABLE tenant_api_keys (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name            VARCHAR(128) NOT NULL,
    key_hash        VARCHAR(64) NOT NULL UNIQUE,
    key_prefix      VARCHAR(16) NOT NULL,
    key_encrypted   TEXT NOT NULL,
    permissions     VARCHAR(255) NOT NULL DEFAULT 'CATALOG_READ,CATALOG_WRITE',
    last_used_at    TIMESTAMPTZ,
    expires_at      TIMESTAMPTZ,
    active          BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_tenant_api_keys_hash ON tenant_api_keys(key_hash);
CREATE INDEX idx_tenant_api_keys_tenant ON tenant_api_keys(tenant_id);

-- 3. Tenant Webhooks (for real-time stock checks and events)
CREATE TABLE tenant_webhooks (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    url             VARCHAR(512) NOT NULL,
    secret          VARCHAR(128),
    event_types     VARCHAR(255) NOT NULL DEFAULT 'STOCK_CHECK,CALL_COMPLETED',
    active          BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_tenant_webhooks_tenant ON tenant_webhooks(tenant_id);

-- Seed initial sample catalog items for the CES tenant
INSERT INTO catalog_items (tenant_id, sku, name, category, price_daily, price_monthly, deposit, unit, in_stock, stock_quantity, specs, description)
VALUES 
('11111111-1111-1111-1111-111111111111', 'JCB-3CX', 'JCB 3CX Ekskavator-Yükləyici', 'Ağır Tikinti Texnikası', 350.00, 7500.00, 500.00, 'gün', true, 3, 'Çalov həcmi: 1.0 m³, Qazma dərinliyi: 4.24 m', 'Universal ekskavator-yükləyici, tikinti və torpaq qazıntı işləri üçün.'),
('11111111-1111-1111-1111-111111111111', 'CAT-320', 'CAT 320 Paletli Ekskavator', 'Ağır Tikinti Texnikası', 600.00, 14000.00, 1000.00, 'gün', true, 2, 'Çəki: 22 ton, Çalov: 1.2 m³', 'Böyük həcmli torpaq qazma və xəndək açma işləri üçün güclü paletli ekskavator.'),
('11111111-1111-1111-1111-111111111111', 'XCMG-25T', 'XCMG 25 Tonluq Avtokran', 'Kranlar və Qaldırıcılar', 450.00, 10500.00, 500.00, 'gün', true, 2, 'Qaldırma gücü: 25 ton, Ox uzunluğu: 39.5 m', 'Yüksək mərtəbəli tikinti və ağır yüklərin montajı üçün avtokran.'),
('11111111-1111-1111-1111-111111111111', 'BOBCAT-S530', 'Bobcat S530 Mini Yükləyici', 'Kompakt Texnika', 220.00, 4800.00, 300.00, 'gün', true, 4, 'Yükqaldırma: 860 kq, Çəki: 2.8 ton', 'Dar sahələrdə, həyətlərdə və anbar daxilində təmizlik və material daşıma üçün mini yükləyici.'),
('11111111-1111-1111-1111-111111111111', 'DYNAPAC-CA250', 'Dynapac CA250 Torpaq Vərdənəsi (Katok)', 'Yol Tikinti Texnikası', 300.00, 6800.00, 400.00, 'gün', true, 1, 'Çəki: 12 ton, Titrəməli (vibrasiyalı)', 'Torpaq, qum və asfaltdan əvvəlki qatların sıxlaşdırılması üçün vibrasiyalı vərdənə.');
