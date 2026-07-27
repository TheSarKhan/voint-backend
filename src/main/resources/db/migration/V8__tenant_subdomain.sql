-- Each tenant gets its own panel address: ces.voint.az, klinika.voint.az, ...
--
-- Worth being explicit about what this is NOT: it is not a separate panel per tenant. One React
-- app is served for every subdomain; the hostname only says which tenant is being looked at, and
-- the JWT still decides what the user may see. Adding a customer therefore touches no server
-- configuration at all - a single wildcard DNS record covers every future tenant.

ALTER TABLE tenants
    ADD COLUMN subdomain VARCHAR(63);

-- Case-insensitive uniqueness: hostnames are case-insensitive, so "CES" and "ces" must not be
-- two different tenants pointing at the same address.
CREATE UNIQUE INDEX idx_tenants_subdomain ON tenants (lower(subdomain));

UPDATE tenants
SET subdomain = 'ces'
WHERE id = '11111111-1111-1111-1111-111111111111';
