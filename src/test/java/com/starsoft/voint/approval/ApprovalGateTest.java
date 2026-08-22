package com.starsoft.voint.approval;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.starsoft.voint.rbac.Permission;
import com.starsoft.voint.rbac.PermissionResolver;
import com.starsoft.voint.rbac.Role;
import com.starsoft.voint.rbac.RolePermission;
import com.starsoft.voint.rbac.RolePermissionRepository;
import com.starsoft.voint.rbac.RoleRepository;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class ApprovalGateTest {

    @Autowired
    private PermissionResolver permissionResolver;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private RolePermissionRepository permissionRepository;

    private static final UUID CES_TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void testDirectExecutionPermissionCheck() {
        // Create an Admin role with APPROVAL:CREATE permission
        Role adminRole = roleRepository.save(Role.builder()
                .tenantId(CES_TENANT_ID)
                .name("Test Baş Menecer")
                .description("Birbaşa icra edən rol")
                .build());

        permissionRepository.save(new RolePermission(adminRole.getId(), Permission.Resource.APPROVAL, Permission.Action.CREATE));
        permissionResolver.evictRole(adminRole.getId());

        boolean adminCanBypass = permissionResolver.isAllowed(adminRole.getId(), Permission.Resource.APPROVAL, Permission.Action.CREATE);
        assertTrue(adminCanBypass, "Admin with APPROVAL:CREATE should bypass approval queue");

        // Create a Junior Operator role WITHOUT APPROVAL:CREATE permission
        Role juniorRole = roleRepository.save(Role.builder()
                .tenantId(CES_TENANT_ID)
                .name("Test Kiçik Operator")
                .description("Təsdiq tələb olunan rol")
                .build());

        permissionRepository.save(new RolePermission(juniorRole.getId(), Permission.Resource.APPROVAL, Permission.Action.READ));
        permissionResolver.evictRole(juniorRole.getId());

        boolean juniorCanBypass = permissionResolver.isAllowed(juniorRole.getId(), Permission.Resource.APPROVAL, Permission.Action.CREATE);
        assertFalse(juniorCanBypass, "Junior role without APPROVAL:CREATE must NOT bypass approval queue");
    }
}
