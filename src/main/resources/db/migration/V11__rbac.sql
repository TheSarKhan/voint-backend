-- Departments, roles and a permission matrix, shared by platform staff and tenant users.
--
-- One set of tables serves both sides. tenant_id IS NULL means "belongs to the platform":
-- a platform department, a template role, a Voint staff account. tenant_id set means it
-- belongs to that business. Two parallel systems would have meant every permission check,
-- every screen and every seed written twice, and they would have drifted.
--
-- Permissions are NOT a table of rows an operator can invent. The resource and action names
-- are an enum in code (see Permission.java), because a permission only means something if an
-- endpoint actually enforces it - a row saying "calls:approve" with no code behind it is a
-- promise the system cannot keep.

CREATE TABLE departments (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- NULL = platform department (Satış, Dəstək...). Set = this business's own.
    tenant_id   UUID REFERENCES tenants (id) ON DELETE CASCADE,
    name        VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Names repeat across tenants ("Registratura" in every clinic) but must be unique within one.
-- A partial index per scope keeps that true without blocking reuse.
CREATE UNIQUE INDEX idx_departments_tenant_name
    ON departments (tenant_id, lower(name)) WHERE tenant_id IS NOT NULL;
CREATE UNIQUE INDEX idx_departments_platform_name
    ON departments (lower(name)) WHERE tenant_id IS NULL;

CREATE TABLE roles (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID REFERENCES tenants (id) ON DELETE CASCADE,
    department_id UUID REFERENCES departments (id) ON DELETE SET NULL,
    name          VARCHAR(120) NOT NULL,
    description   VARCHAR(500),
    -- A template is a platform role a business can copy into itself as a starting point.
    is_template   BOOLEAN NOT NULL DEFAULT false,
    -- Built-in roles the platform depends on; renaming is fine, deleting is not.
    is_system     BOOLEAN NOT NULL DEFAULT false,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_roles_tenant_name
    ON roles (tenant_id, lower(name)) WHERE tenant_id IS NOT NULL;
CREATE UNIQUE INDEX idx_roles_platform_name
    ON roles (lower(name)) WHERE tenant_id IS NULL;

-- The matrix. One row per allowed (role, resource, action); absence means denied.
-- Storing only grants keeps "what can this role do" readable at a glance in the database.
CREATE TABLE role_permissions (
    role_id  UUID        NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    resource VARCHAR(40) NOT NULL,
    action   VARCHAR(20) NOT NULL,
    PRIMARY KEY (role_id, resource, action)
);

-- panel_users joins the model.
ALTER TABLE panel_users
    ADD COLUMN role_id UUID REFERENCES roles (id) ON DELETE RESTRICT,
    -- ACTIVE / BLOCKED. A blocked user keeps their history and can be let back in;
    -- deleting them would orphan whatever they created.
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN full_name VARCHAR(160),
    ADD COLUMN last_login_at TIMESTAMPTZ;

-- tenant_id must become nullable for platform staff. V3 already did this for the seeded
-- SUPER_ADMIN; repeated here so a database built from scratch reaches the same shape.
ALTER TABLE panel_users ALTER COLUMN tenant_id DROP NOT NULL;

-- Tenants can be archived rather than deleted: call transcripts and invoices have to survive
-- a cancelled customer, both for accounting and for letting them come back.
ALTER TABLE tenants
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN archived_at TIMESTAMPTZ;

-- ---------------------------------------------------------------- seed

-- Platform: the account that runs Voint itself.
INSERT INTO roles (id, tenant_id, name, description, is_system)
VALUES ('a0000000-0000-0000-0000-000000000001', NULL, 'Platforma admini',
        'Bütün müəssisələr, provayder açarları və hesablaşma üzərində tam səlahiyyət', true);

-- Tenant template: what a business owner gets. Copied into each new tenant on creation.
INSERT INTO roles (id, tenant_id, name, description, is_template, is_system)
VALUES ('a0000000-0000-0000-0000-000000000002', NULL, 'Sahib',
        'Öz müəssisəsində tam səlahiyyət: zənglər, müştərilər, bilik bazası, hesablaşma, işçilər',
        true, true);

-- Second template, deliberately narrower: a receptionist has no business seeing the invoice.
INSERT INTO roles (id, tenant_id, name, description, is_template, is_system)
VALUES ('a0000000-0000-0000-0000-000000000003', NULL, 'Operator',
        'Zənglər, müştərilər və rezervasiyalar. Hesablaşma və ayarlar görünmür.', true, true);

-- Platform admin: everything, everywhere.
INSERT INTO role_permissions (role_id, resource, action)
SELECT 'a0000000-0000-0000-0000-000000000001', r, a
FROM unnest(ARRAY['TENANT','USER','ROLE','CALL','CUSTOMER','RESERVATION','RAG','BILLING',
                  'SETTINGS','LEAD','PROVIDER']) r
CROSS JOIN unnest(ARRAY['READ','CREATE','UPDATE','DELETE']) a;

-- Sahib: everything inside their own tenant. No TENANT/LEAD/PROVIDER - those are platform-level.
INSERT INTO role_permissions (role_id, resource, action)
SELECT 'a0000000-0000-0000-0000-000000000002', r, a
FROM unnest(ARRAY['USER','ROLE','CALL','CUSTOMER','RESERVATION','RAG','BILLING','SETTINGS']) r
CROSS JOIN unnest(ARRAY['READ','CREATE','UPDATE','DELETE']) a;

-- Operator: day-to-day work only, and no deleting.
INSERT INTO role_permissions (role_id, resource, action)
SELECT 'a0000000-0000-0000-0000-000000000003', r, a
FROM unnest(ARRAY['CALL','CUSTOMER','RESERVATION']) r
CROSS JOIN unnest(ARRAY['READ','CREATE','UPDATE']) a;

-- Existing accounts keep working: map the old string role onto the new rows.
UPDATE panel_users SET role_id = 'a0000000-0000-0000-0000-000000000001' WHERE role = 'SUPER_ADMIN';
UPDATE panel_users SET role_id = 'a0000000-0000-0000-0000-000000000002' WHERE role_id IS NULL;
