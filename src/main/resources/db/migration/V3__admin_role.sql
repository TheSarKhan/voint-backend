-- Support a platform-wide SUPER_ADMIN role that is not scoped to any single tenant.

-- panel_users.tenant_id must become nullable: a SUPER_ADMIN user has tenant_id = NULL.
ALTER TABLE panel_users ALTER COLUMN tenant_id DROP NOT NULL;

-- Seed a platform-wide SUPER_ADMIN panel user for the admin panel.
-- Email: platform@voint.az. Password is NOT committed here (unlike the public CES demo
-- login) since this account has real cross-tenant access - shared out of band. Change it
-- after first login.
INSERT INTO panel_users (tenant_id, email, password_hash, role)
VALUES (
    NULL,
    'platform@voint.az',
    '$2a$10$EeV4KuQHgrYzKFAZgUJ6C..Nc7VUmLUT8WoHqOGamzLnZ8qnkFVMe',
    'SUPER_ADMIN'
);
