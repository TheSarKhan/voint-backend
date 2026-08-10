package com.starsoft.voint.rbac;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.starsoft.voint.common.exception.NotFoundException;
import com.starsoft.voint.rbac.dto.DepartmentCopyResponse;
import com.starsoft.voint.rbac.dto.DepartmentDetail;
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
    private final DepartmentRepository departmentRepository;
    private final PermissionResolver permissions;
    private final com.starsoft.voint.auth.PanelUserRepository userRepository;

    /**
     * Roles this business may hand out: its own, plus the platform templates.
     *
     * <p>Both, always. This used to return the templates only while a business had no roles of its
     * own, which quietly broke the moment it got one: users already sitting on a template - which
     * is every first owner account - had a role that was no longer in the list they were displayed
     * against. The screen then showed them as something they were not, and one careless click
     * reassigned them. A template is by definition a role offered to businesses; whether a business
     * also has bespoke roles does not change that.
     */
    @Transactional(readOnly = true)
    public List<Role> assignableFor(UUID tenantId) {
        List<Role> out = new ArrayList<>(roleRepository.findByTenantIdOrderByName(tenantId));
        Set<UUID> seen = out.stream().map(Role::getId).collect(java.util.stream.Collectors.toSet());
        for (Role template : roleRepository.findByTemplateTrueOrderByName()) {
            if (seen.add(template.getId())) {
                out.add(template);
            }
        }
        return out;
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
                .orElseGet(() -> copyTemplate(OWNER_TEMPLATE_ID, tenantId, true, null));
    }

    @Transactional(readOnly = true)
    public List<RoleDetail> detailsFor(UUID tenantId) {
        List<Role> roles = tenantId == null
                ? roleRepository.findByTenantIdIsNullOrderByName()
                : roleRepository.findByTenantIdOrderByName(tenantId);
        return roles.stream().map(this::toDetail).toList();
    }

    /** A template department's own roles - the picklist shown before copying it into a tenant. */
    @Transactional(readOnly = true)
    public List<RoleDetail> detailsForDepartment(UUID departmentId) {
        return roleRepository.findByDepartmentIdOrderByName(departmentId).stream()
                .map(this::toDetail).toList();
    }

    @Transactional
    public RoleDetail create(RoleUpsertRequest request) {
        String name = request.name().trim();
        requireNameFree(request.tenantId(), name, null);
        Role role = roleRepository.save(Role.builder()
                .tenantId(request.tenantId())
                .departmentId(resolveDepartment(request.departmentId(), request.tenantId()))
                .name(name)
                .description(request.description())
                .template(request.template())
                .build());
        replacePermissions(role.getId(), role.getTenantId(), request.permissions());
        return toDetail(role);
    }

    @Transactional
    public RoleDetail update(UUID roleId, RoleUpsertRequest request) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> NotFoundException.of("Rol", roleId));
        String name = request.name().trim();
        requireNameFree(role.getTenantId(), name, roleId);
        role.setName(name);
        role.setDescription(request.description());
        // Checked against the role's own owner, not the request's: a payload claiming a different
        // tenant must not be able to move a role somewhere it does not belong.
        role.setDepartmentId(resolveDepartment(request.departmentId(), role.getTenantId()));
        role = roleRepository.save(role);
        replacePermissions(roleId, role.getTenantId(), request.permissions());
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

    /**
     * The operator copying a template by hand, as opposed to the automatic copy a new business
     * gets. The result is an ordinary role: not marked system, so it can be deleted again if the
     * copy turns out to be the wrong starting point.
     *
     * <p>{@code departmentId} is optional and must be one of the TARGET tenant's own departments
     * (never the template's own - see {@link #resolveDepartment}). A name collision with an
     * existing role of the tenant is not an error here: it is renamed ("Operator" -&gt;
     * "Operator 2") rather than rejected, because refusing the whole import over one clashing name
     * is worse than a duplicate the operator can rename or delete afterward.
     */
    @Transactional
    public RoleDetail copyTemplateTo(UUID templateId, UUID tenantId, UUID departmentId) {
        Role template = roleRepository.findById(templateId)
                .orElseThrow(() -> NotFoundException.of("Şablon rol", templateId));
        if (template.getTenantId() != null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Bu, bir müəssisənin öz roludur - şablon deyil, köçürülə bilməz.");
        }
        UUID resolvedDepartment = resolveDepartment(departmentId, tenantId);
        Role copy = copyTemplate(templateId, tenantId, false, resolvedDepartment);
        if (copy == null) {
            throw NotFoundException.of("Şablon rol", templateId);
        }
        return toDetail(copy);
    }

    /**
     * Copies a whole template department into a tenant: a new department of its own, plus a copy
     * of every selected role from the template. {@code roleIds} null means "all of them" - the
     * default the screen offers, since leaving a role behind by accident is easy to miss and
     * leaving one in is easy to delete afterward if it turns out unwanted.
     */
    @Transactional
    public DepartmentCopyResponse copyDepartmentTo(UUID templateDepartmentId, UUID tenantId, List<UUID> roleIds) {
        Department template = departmentRepository.findById(templateDepartmentId)
                .orElseThrow(() -> NotFoundException.of("Departament", templateDepartmentId));
        if (template.getTenantId() != null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Bu, bir müəssisənin öz departamentidir - şablon deyil, köçürülə bilməz.");
        }
        Department copy = departmentRepository.save(Department.builder()
                .tenantId(tenantId)
                .name(uniqueDepartmentNameFor(tenantId, template.getName()))
                .description(template.getDescription())
                .build());

        List<RoleDetail> copiedRoles = new ArrayList<>();
        for (Role templateRole : roleRepository.findByDepartmentIdOrderByName(templateDepartmentId)) {
            if (roleIds != null && !roleIds.contains(templateRole.getId())) {
                continue;
            }
            Role roleCopy = copyTemplate(templateRole.getId(), tenantId, false, copy.getId());
            if (roleCopy != null) {
                copiedRoles.add(toDetail(roleCopy));
            }
        }
        return new DepartmentCopyResponse(toDepartmentDetail(copy), copiedRoles);
    }

    /**
     * A department belongs either to the platform or to one business, and so does a role. Letting
     * them cross would put a business's role inside a platform department - visible in a list it
     * has no business appearing in - so a mismatch is refused rather than quietly dropped.
     */
    private UUID resolveDepartment(UUID departmentId, UUID tenantId) {
        if (departmentId == null) {
            return null;
        }
        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> NotFoundException.of("Departament", departmentId));
        if (!java.util.Objects.equals(department.getTenantId(), tenantId)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Bu departament başqa müəssisəyə aiddir.");
        }
        return departmentId;
    }

    /**
     * The database enforces this too, but a constraint violation surfaces as an unexplained 500.
     * Saying which name is taken is the difference between a fixable message and a dead end.
     */
    private void requireNameFree(UUID tenantId, String name, UUID exceptRoleId) {
        roleRepository.findByTenantIdAndNameIgnoreCase(tenantId, name)
                .filter(existing -> !existing.getId().equals(exceptRoleId))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "'" + existing.getName() + "' adlı rol artıq var.");
                });
    }

    /**
     * Replaces the whole matrix rather than patching it: the editor always submits the full grid
     * it is displaying, so a merge would silently keep a permission the operator just unticked.
     *
     * <p>A tenant-owned role (tenantId != null) may never hold a platform-only resource (TENANT,
     * LEAD, PROVIDER - see {@link Permission.Resource#isPlatformOnly()}): granting one would let
     * that tenant's own role escalate into platform actions the moment anything stops enforcing
     * this at the controller layer, as happened before RoleController/DepartmentController grew
     * their requireSuperAdmin() calls. Caught here too, defense in depth.
     */
    private void replacePermissions(UUID roleId, UUID tenantId, Map<String, List<String>> grid) {
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
            if (tenantId != null && r.isPlatformOnly()) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "'" + r.getLabel() + "' yalnız platforma rollarına verilə bilər.");
            }
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
        String departmentName = role.getDepartmentId() == null ? null
                : departmentRepository.findById(role.getDepartmentId())
                        .map(Department::getName).orElse(null);

        return new RoleDetail(role.getId(), role.getTenantId(),
                role.getDepartmentId(), departmentName, role.getName(),
                role.getDescription(), role.isTemplate(), role.isSystem(),
                countUsers(role.getId()), grid);
    }

    private long countUsers(UUID roleId) {
        return userRepository.countByRoleId(roleId);
    }

    /**
     * @param system       true only for the owner role a new business is provisioned with, which
     *                     must survive: deleting it would leave the business with nothing to
     *                     assign anyone. A template the operator copies by hand is an ordinary,
     *                     deletable role.
     * @param departmentId the TARGET tenant's own department (already resolved/validated by the
     *                     caller), or null for ungrouped. Never the template's own department -
     *                     that one belongs to the platform, and the business being copied into
     *                     cannot see it.
     */
    private Role copyTemplate(UUID templateId, UUID tenantId, boolean system, UUID departmentId) {
        Role template = roleRepository.findById(templateId).orElse(null);
        if (template == null) {
            log.error("Owner template {} is missing - tenant {} will have no role to assign",
                    templateId, tenantId);
            return null;
        }
        Role copy = roleRepository.save(Role.builder()
                .tenantId(tenantId)
                .departmentId(departmentId)
                .name(uniqueNameFor(tenantId, template.getName()))
                .description(template.getDescription())
                .system(system)
                .build());

        permissionRepository.saveAll(permissionsOf(templateId).stream()
                .map(p -> new RolePermission(copy.getId(), p.getResource(), p.getAction()))
                .toList());

        log.info("Created '{}' role for tenant {}", copy.getName(), tenantId);
        return copy;
    }

    /**
     * Appends " 2", " 3"... until the name is free in this tenant. Copy paths use this instead of
     * {@link #requireNameFree} on purpose: refusing an entire template/department import because
     * one role's name happens to collide is worse than a harmless duplicate the operator can
     * rename or delete afterward.
     */
    private String uniqueNameFor(UUID tenantId, String baseName) {
        String candidate = baseName;
        int n = 2;
        while (roleRepository.existsByTenantIdAndNameIgnoreCase(tenantId, candidate)) {
            candidate = baseName + " " + n;
            n++;
        }
        return candidate;
    }

    private String uniqueDepartmentNameFor(UUID tenantId, String baseName) {
        String candidate = baseName;
        int n = 2;
        while (departmentRepository.existsByTenantIdAndNameIgnoreCase(tenantId, candidate)) {
            candidate = baseName + " " + n;
            n++;
        }
        return candidate;
    }

    private DepartmentDetail toDepartmentDetail(Department d) {
        return new DepartmentDetail(d.getId(), d.getTenantId(), d.getName(), d.getDescription(),
                roleRepository.countByDepartmentId(d.getId()));
    }
}
