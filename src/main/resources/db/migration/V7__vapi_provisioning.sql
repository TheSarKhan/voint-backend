-- Let the platform create and maintain each tenant's Vapi assistant instead of an operator
-- filling in 22 settings by hand in someone else's dashboard.
--
-- The setting that matters most is metadata.tenant_id. Without it the voice webhook falls back to
-- the bootstrap tenant, so a newly-added business would answer its callers out of CES's knowledge
-- base - fluently, confidently and wrongly, with nothing logged as an error. Provisioning exists
-- to make that mistake unreachable rather than merely unlikely.

ALTER TABLE tenants
    -- Vapi's id for this tenant's assistant; null until it has been provisioned.
    ADD COLUMN vapi_assistant_id VARCHAR(64),
    -- Speech-to-text hints, per tenant. These MUST NOT be shared: giving a clinic the equipment
    -- vocabulary below would bias its transcription toward "ekskavator" every time a caller says
    -- something that sounds vaguely similar, making recognition worse rather than better.
    ADD COLUMN stt_domain     VARCHAR(255),
    ADD COLUMN stt_topic      VARCHAR(512),
    -- Comma-separated industry terms. Azerbaijani place names are added by the platform for
    -- everyone (see VapiAssistantProvisioner) and do not belong here.
    ADD COLUMN stt_vocabulary TEXT;

-- CES: keep exactly the assistant that already exists, and the vocabulary that was tuned against
-- real calls, so provisioning takes it over without changing how it behaves.
UPDATE tenants
SET vapi_assistant_id = '8dea9b07-b196-4fa7-9f28-578f8425279e',
    stt_domain        = 'Texnika icarəsi (tikinti texnikası) — Azərbaycan',
    stt_topic         = 'Müştəri texnika icarəsi qiymətləri, icarə müddəti, çatdırılma ünvanı və depozit haqqında soruşur',
    stt_vocabulary    = 'kirayə,icarə,ekskavator,buldozer,yükləyici,JCB,CAT,Kubota,depozit,operator,çatdırılma,texnika,rezervasiya,günlük,həftəlik,manat'
WHERE id = '11111111-1111-1111-1111-111111111111';
