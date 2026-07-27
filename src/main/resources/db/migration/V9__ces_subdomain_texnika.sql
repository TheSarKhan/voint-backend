-- CES's panel address becomes texnika.<domain> instead of ces.<domain>.
--
-- Done as a new migration rather than by editing V8: that one has already run in production, and
-- changing an applied migration fails the checksum and stops the next deploy. Seed values are
-- data, so they get corrected forward.

UPDATE tenants
SET subdomain = 'texnika'
WHERE id = '11111111-1111-1111-1111-111111111111'
  AND subdomain = 'ces';
