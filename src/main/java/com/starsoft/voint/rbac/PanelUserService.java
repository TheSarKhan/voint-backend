package com.starsoft.voint.rbac;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.starsoft.voint.auth.PanelUser;
import com.starsoft.voint.auth.PanelUserRepository;
import com.starsoft.voint.common.exception.NotFoundException;
import com.starsoft.voint.rbac.dto.PanelUserCreateRequest;
import com.starsoft.voint.rbac.dto.PanelUserCreatedResponse;
import com.starsoft.voint.rbac.dto.PanelUserResponse;
import com.starsoft.voint.rbac.dto.PanelUserUpdateRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Panel accounts, scoped to a business.
 *
 * <p>Until now the only way to give a new customer a login was an INSERT on the server with a
 * hand-made bcrypt hash. That single gap is what stopped a second business being onboarded at all.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PanelUserService {

    /**
     * Deliberately excludes characters that get misread when a password is dictated over the
     * phone or copied off a screen: 0/O, 1/l/I. The operator will be reading this aloud.
     */
    private static final String ALPHABET = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int PASSWORD_LENGTH = 12;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final PanelUserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final PermissionResolver permissions;
    private final com.starsoft.voint.mail.MailService mail;
    private final com.starsoft.voint.tenant.TenantRepository tenantRepository;
    private final com.starsoft.voint.settings.PlatformSettingsService settings;
    private final com.starsoft.voint.settings.PanelUrls panelUrls;

    @Transactional(readOnly = true)
    public List<PanelUserResponse> listForTenant(UUID tenantId) {
        List<PanelUser> users = userRepository.findByTenantIdOrderByCreatedAt(tenantId);
        Map<UUID, String> roleNames = roleNamesFor(users);
        return users.stream()
                .map(u -> PanelUserResponse.from(u, roleNames.get(u.getRoleId())))
                .toList();
    }

    @Transactional
    public PanelUserCreatedResponse create(UUID tenantId, PanelUserCreateRequest request) {
        String email = request.email().trim().toLowerCase();

        // Emails are unique platform-wide, not per tenant: login has no tenant context - the
        // user just types an address - so the same address cannot belong to two businesses.
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Bu e-poçt artıq istifadə olunur: " + email);
        }

        Role role = requireAssignableRole(request.roleId(), tenantId);
        String password = generatePassword();

        PanelUser user = PanelUser.builder()
                .tenantId(tenantId)
                .email(email)
                .fullName(request.fullName() != null ? request.fullName().trim() : null)
                .passwordHash(passwordEncoder.encode(password))
                .roleId(role.getId())
                // Legacy string role, still read by the JWT and TenantAccessGuard.
                .role(tenantId == null ? "SUPER_ADMIN" : "ADMIN")
                .status("ACTIVE")
                .build();
        user = userRepository.save(user);

        log.info("Created panel user {} for tenant {} with role '{}'", email, tenantId, role.getName());

        boolean emailed = tryEmail(email, tenantId, password, true);
        // The password is returned only when it could NOT be sent. Putting it in the response
        // regardless would mean it appears on a screen the operator no longer needs to read,
        // and gets copied into places it should not live.
        return new PanelUserCreatedResponse(
                PanelUserResponse.from(user, role.getName()), emailed ? null : password, emailed);
    }

    /**
     * Issues a new password. The old one becomes invalid immediately - there is no "send a reset
     * link" step yet, so this is what an operator uses when a customer is locked out.
     */
    @Transactional
    public PanelUserCreatedResponse resetPassword(UUID tenantId, UUID userId) {
        PanelUser user = requireUserOfTenant(tenantId, userId);
        String password = generatePassword();
        user.setPasswordHash(passwordEncoder.encode(password));
        user = userRepository.save(user);
        log.info("Reset password for panel user {}", user.getEmail());
        boolean emailed = tryEmail(user.getEmail(), user.getTenantId(), password, false);
        return new PanelUserCreatedResponse(
                PanelUserResponse.from(user, roleName(user.getRoleId())),
                emailed ? null : password, emailed);
    }

    /**
     * Corrects an account's address or name - a typo at onboarding, or someone who changed their
     * surname. Nothing else moves: the role, the status and the password stay exactly as they were.
     *
     * <p>The email IS the login, so changing it logs the person out of any session they have open.
     * That is correct - the token names an address that no longer exists - but it only actually
     * happens if the old address is evicted from the permission cache, which is why the eviction
     * below uses the previous value and not the saved one.
     */
    @Transactional
    public PanelUserResponse update(UUID tenantId, UUID userId, PanelUserUpdateRequest request) {
        PanelUser user = requireUserOfTenant(tenantId, userId);
        String previousEmail = user.getEmail();
        String email = request.email().trim().toLowerCase();

        if (!email.equalsIgnoreCase(previousEmail)
                && userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Bu e-poçt artıq istifadə olunur: " + email);
        }

        user.setEmail(email);
        String fullName = request.fullName() != null ? request.fullName().trim() : null;
        user.setFullName(fullName == null || fullName.isEmpty() ? null : fullName);
        user = userRepository.save(user);

        permissions.evictUser(previousEmail);
        permissions.evictUser(user);

        if (!email.equalsIgnoreCase(previousEmail)) {
            log.info("Panel user {} is now {}", previousEmail, email);
        }
        return PanelUserResponse.from(user, roleName(user.getRoleId()));
    }

    @Transactional
    public PanelUserResponse setStatus(UUID tenantId, UUID userId, String status) {
        if (!"ACTIVE".equals(status) && !"BLOCKED".equals(status)) {
            throw new IllegalArgumentException("status yalnız ACTIVE və ya BLOCKED ola bilər");
        }
        PanelUser user = requireUserOfTenant(tenantId, userId);
        user.setStatus(status);
        user = userRepository.save(user);
        // Blocking has to take effect on the next request, not when the token happens to expire.
        permissions.evictUser(user);
        return PanelUserResponse.from(user, roleName(user.getRoleId()));
    }

    @Transactional
    public PanelUserResponse changeRole(UUID tenantId, UUID userId, UUID roleId) {
        PanelUser user = requireUserOfTenant(tenantId, userId);
        Role role = requireAssignableRole(roleId, tenantId);
        user.setRoleId(role.getId());
        user = userRepository.save(user);
        permissions.evictUser(user);
        return PanelUserResponse.from(user, role.getName());
    }

    @Transactional
    public void delete(UUID tenantId, UUID userId) {
        PanelUser user = requireUserOfTenant(tenantId, userId);

        // Leaving a business with no way in is a support call waiting to happen.
        long remaining = userRepository.findByTenantIdOrderByCreatedAt(tenantId).stream()
                .filter(u -> !u.getId().equals(userId))
                .filter(u -> "ACTIVE".equals(u.getStatus()))
                .count();
        if (remaining == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Bu, müəssisənin son aktiv istifadəçisidir - silinsə panelə giriş qalmır. "
                            + "Əvvəlcə başqa istifadəçi yarat.");
        }

        userRepository.delete(user);
        permissions.evictUser(user);
        log.info("Deleted panel user {}", user.getEmail());
    }

    /**
     * @return true if the message actually left. A failure here must not undo the account - the
     *         password is already set, so losing the user because the mail server hiccuped would
     *         be the worse outcome. The caller shows the password instead.
     */
    private boolean tryEmail(String email, UUID tenantId, String password, boolean welcome) {
        if (!mail.isConfigured()) {
            return false;
        }
        try {
            String tenantName = tenantId == null ? "Voint"
                    : tenantRepository.findById(tenantId)
                            .map(com.starsoft.voint.tenant.Tenant::getName).orElse("Voint");
            String url = panelUrl(tenantId);
            mail.send(email,
                    welcome ? "Voint panelinizə giriş" : "Voint şifrəniz yeniləndi",
                    welcome
                            ? com.starsoft.voint.mail.MailTemplates.welcome(tenantName, url, email, password)
                            : com.starsoft.voint.mail.MailTemplates.passwordReset(url, email, password));
            return true;
        } catch (Exception e) {
            log.error("Could not email credentials to {} - falling back to showing them: {}",
                    email, e.getMessage());
            return false;
        }
    }

    /** The tenant's own address when it has a subdomain, otherwise the shared panel. */
    private String panelUrl(UUID tenantId) {
        // Domen məntiqi PanelUrls-ə köçürüldü (əvvəl burada voint-admin./voint. hardcode idi
        // və domen dəyişəndə qırıldı).
        return panelUrls.forTenant(tenantId);
    }

    // ---------------------------------------------------------------- helpers

    private PanelUser requireUserOfTenant(UUID tenantId, UUID userId) {
        PanelUser user = userRepository.findById(userId)
                .orElseThrow(() -> NotFoundException.of("İstifadəçi", userId));
        // The path says which tenant; the record has to agree, or one business could act on
        // another's accounts by guessing an id.
        if (tenantId != null && !tenantId.equals(user.getTenantId())) {
            throw NotFoundException.of("İstifadəçi", userId);
        }
        return user;
    }

    /** A role may be assigned only if it belongs to this tenant or is a platform template. */
    private Role requireAssignableRole(UUID roleId, UUID tenantId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> NotFoundException.of("Rol", roleId));
        boolean own = tenantId != null && tenantId.equals(role.getTenantId());
        boolean platformRole = role.getTenantId() == null;
        if (!own && !platformRole) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Bu rol başqa müəssisəyə aiddir");
        }
        return role;
    }

    private String roleName(UUID roleId) {
        return roleId == null ? null
                : roleRepository.findById(roleId).map(Role::getName).orElse(null);
    }

    private Map<UUID, String> roleNamesFor(List<PanelUser> users) {
        List<UUID> ids = users.stream().map(PanelUser::getRoleId).filter(java.util.Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return roleRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Role::getId, Role::getName, (a, b) -> a));
    }

    private String generatePassword() {
        StringBuilder sb = new StringBuilder(PASSWORD_LENGTH);
        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
