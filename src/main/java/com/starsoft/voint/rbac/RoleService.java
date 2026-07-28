package com.starsoft.voint.rbac;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Roles available to a business: its own, plus the platform templates it can pick from. */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleService {

    /** Seeded in V11. Copied into every new tenant so its first user has something to be. */
    public static final UUID OWNER_TEMPLATE_ID =
            UUID.fromString("a0000000-0000-0000-0000-000000000002");

    private final RoleRepository roleRepository;
    private final RolePermissionRepository permissionRepository;

    @Transactional(readOnly = true)
    public List<Role> assignableFor(UUID tenantId) {
        List<Role> own = roleRepository.findByTenantIdOrderByName(tenantId);
        if (!own.isEmpty()) {
            return own;
        }
        // A tenant with no roles of its own can still use the platform templates, so a business
        // is never in a state where no role can be assigned to its first user.
        return roleRepository.findByTemplateTrueOrderByName();
    }

    @Transactional(readOnly = true)
    public List<RolePermission> permissionsOf(UUID roleId) {
        return permissionRepository.findByRoleId(roleId);
    }

    /**
     * Gives a new business its own copy of the owner template.
     *
     * <p>A copy rather than a shared reference: the business may later narrow or widen what its
     * owner can do, and that must not change every other business on the platform.
     */
    @Transactional
    public Role createOwnerRoleFor(UUID tenantId) {
        return roleRepository.findByTenantIdAndNameIgnoreCase(tenantId, "Sahib")
                .orElseGet(() -> copyTemplate(OWNER_TEMPLATE_ID, tenantId));
    }

    private Role copyTemplate(UUID templateId, UUID tenantId) {
        Role template = roleRepository.findById(templateId).orElse(null);
        if (template == null) {
            log.error("Owner template {} is missing - tenant {} will have no role to assign",
                    templateId, tenantId);
            return null;
        }
        Role copy = roleRepository.save(Role.builder()
                .tenantId(tenantId)
                .name(template.getName())
                .description(template.getDescription())
                .system(true)
                .build());

        permissionRepository.saveAll(permissionsOf(templateId).stream()
                .map(p -> new RolePermission(copy.getId(), p.getResource(), p.getAction()))
                .toList());

        log.info("Created '{}' role for tenant {}", copy.getName(), tenantId);
        return copy;
    }
}
