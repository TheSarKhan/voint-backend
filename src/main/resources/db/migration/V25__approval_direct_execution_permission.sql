-- ============================================================================
-- V25: Direct Execution (Bypass Approval Hold) Permission for Roles
-- ============================================================================

-- Grant APPROVAL:CREATE (Direct execution without approval hold) to Sahib (Owner)
-- and Platform Admin roles.
INSERT INTO role_permissions (role_id, resource, action)
SELECT id, 'APPROVAL', 'CREATE'
FROM roles
WHERE name = 'Sahib' OR id = 'a0000000-0000-0000-0000-000000000001'
ON CONFLICT DO NOTHING;
