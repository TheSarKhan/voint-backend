package com.starsoft.voint.rbac;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.starsoft.voint.auth.PanelUser;
import com.starsoft.voint.auth.PanelUserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Answers "may this person do this?" on every request, without a database round trip each time.
 *
 * <p>The role and status are read from the database rather than from the JWT, and that is the
 * whole point: a token lives for an hour, so trusting its claims would mean a blocked account
 * keeps working for an hour after being blocked, and a demoted user keeps their old powers just
 * as long. Revocation has to be immediate or it is not revocation.
 *
 * <p>Both lookups are cached and evicted on change, so the normal path costs a map read. The cache
 * is per-process: with one backend that is exact, and if a second instance is ever added the fix
 * is to broadcast evictions, not to drop the cache and pay a query per request.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionResolver {

    private final RolePermissionRepository permissionRepository;
    private final PanelUserRepository userRepository;

    private final Map<UUID, Map<Permission.Resource, Set<Permission.Action>>> byRole =
            new ConcurrentHashMap<>();
    private final Map<String, CachedUser> byEmail = new ConcurrentHashMap<>();

    /** What the interceptor needs about the caller, as the database currently sees them. */
    public record CachedUser(UUID roleId, String status, UUID tenantId) {
        public boolean blocked() {
            return "BLOCKED".equals(status);
        }
    }

    @Transactional(readOnly = true)
    public Optional<CachedUser> currentUser(String email) {
        if (email == null) {
            return Optional.empty();
        }
        String key = email.toLowerCase();
        CachedUser cached = byEmail.get(key);
        if (cached != null) {
            return Optional.of(cached);
        }
        return userRepository.findByEmailIgnoreCase(email).map(u -> {
            CachedUser fresh = new CachedUser(u.getRoleId(), u.getStatus(), u.getTenantId());
            byEmail.put(key, fresh);
            return fresh;
        });
    }

    @Transactional(readOnly = true)
    public boolean isAllowed(UUID roleId, Permission.Resource resource, Permission.Action action) {
        if (roleId == null) {
            return false;
        }
        return permissionsOf(roleId).getOrDefault(resource, Set.of()).contains(action);
    }

    @Transactional(readOnly = true)
    public Map<Permission.Resource, Set<Permission.Action>> permissionsOf(UUID roleId) {
        return byRole.computeIfAbsent(roleId, id -> {
            Map<Permission.Resource, Set<Permission.Action>> map =
                    new EnumMap<>(Permission.Resource.class);
            for (RolePermission p : permissionRepository.findByRoleId(id)) {
                map.computeIfAbsent(p.getResource(), r -> EnumSet.noneOf(Permission.Action.class))
                        .add(p.getAction());
            }
            return Collections.unmodifiableMap(map);
        });
    }

    /** Call after editing a role's matrix. */
    public void evictRole(UUID roleId) {
        byRole.remove(roleId);
        log.debug("Permission cache evicted for role {}", roleId);
    }

    /** Call after changing a user's role or status - blocking must bite on the next request. */
    public void evictUser(String email) {
        if (email != null) {
            byEmail.remove(email.toLowerCase());
        }
    }

    public void evictUser(PanelUser user) {
        evictUser(user.getEmail());
    }

    public void evictAll() {
        byRole.clear();
        byEmail.clear();
    }
}
