package com.starsoft.voint.rbac;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.starsoft.voint.common.exception.NotFoundException;
import com.starsoft.voint.rbac.dto.RoleDetail;
import com.starsoft.voint.rbac.dto.RoleUpsertRequest;

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
    private final PermissionResolver permissions;
    private final com.starsoft.voint.auth.PanelUserRepository userRepository;

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

    @Transactional(readOnly = true)
    public List<RoleDetail> detailsFor(UUID tenantId) {
        List<Role> roles = tenantId == null
                ? roleRepository.findByTenantIdIsNullOrderByName()
                : roleRepository.findByTenantIdOrderByName(tenantId);
        return roles.stream().map(this::toDetail).toList();
    }

    @Transactional
    public RoleDetail create(RoleUpsertRequest request) {
        Role role = roleRepository.save(Role.builder()
                .tenantId(request.tenantId())
                .name(request.name().trim())
                .description(request.description())
                .template(request.template())
                .build());
        replacePermissions(role.getId(), request.permissions());
        return toDetail(role);
    }

    @Transactional
    public RoleDetail update(UUID roleId, RoleUpsertRequest request) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> NotFoundException.of("Rol", roleId));
        role.setName(request.name().trim());
        role.setDescription(request.description());
        role = roleRepository.save(role);
        replacePermissions(roleId, request.permissions());
        return toDetail(role);
    }

    @Transactional
    public void delete(UUID roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> NotFoundException.of("Rol", roleId));
        if (role.isSystem()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Bu, sistem roludur - adını dəyişə bilərsən, amma silmək olmaz.");
        }
        long inUse = countUsers(roleId);
        if (inUse > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Bu rol " + inUse + " istifadəçidə işlənir. Əvvəlcə onlara başqa rol ver.");
        }
        permissionRepository.deleteByRoleId(roleId);
        roleRepository.delete(role);
        permissions.evictRole(roleId);
    }

    @Transactional
    public RoleDetail copyTemplateTo(UUID templateId, UUID tenantId) {
        Role copy = copyTemplate(templateId, tenantId);
        if (copy == null) {
            throw NotFoundException.of("Şablon rol", templateId);
        }
        return toDetail(copy);
    }

    /**
     * Replaces the whole matrix rather than patching it: the editor always submits the full grid
     * it is displaying, so a merge would silently keep a permission the operator just unticked.
     */
    private void replacePermissions(UUID roleId, Map<String, List<String>> grid) {
        permissionRepository.deleteByRoleId(roleId);
        if (grid == null || grid.isEmpty()) {
            permissions.evictRole(roleId);
            return;
        }
        List<RolePermission> rows = new ArrayList<>();
        grid.forEach((resource, actions) -> {
            if (actions == null) {
                return;
            }
            Permission.Resource r = Permission.Resource.valueOf(resource);
            actions.forEach(a -> rows.add(
                    new RolePermission(roleId, r, Permission.Action.valueOf(a))));
        });
        permissionRepository.saveAll(rows);
        // The next request must see the new rules, not the ones cached a moment ago.
        permissions.evictRole(roleId);
    }

    private RoleDetail toDetail(Role role) {
        Map<String, List<String>> grid = new LinkedHashMap<>();
        for (RolePermission p : permissionRepository.findByRoleId(role.getId())) {
            grid.computeIfAbsent(p.getResource().name(), k -> new ArrayList<>())
                    .add(p.getAction().name());
        }
        return new RoleDetail(role.getId(), role.getTenantId(), role.getName(),
                role.getDescription(), role.isTemplate(), role.isSystem(),
                countUsers(role.getId()), grid);
    }

    private long countUsers(UUID roleId) {
        return userRepository.findAll().stream()
                .filter(u -> roleId.equals(u.getRoleId()))
                .count();
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
