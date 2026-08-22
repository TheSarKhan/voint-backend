package com.starsoft.voint.tenant;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.starsoft.voint.common.exception.NotFoundException;
import com.starsoft.voint.provisioning.VapiAssistantProvisioner;
import com.starsoft.voint.rbac.RoleService;
import com.starsoft.voint.tenant.dto.TenantConfigUpdateRequest;
import com.starsoft.voint.tenant.dto.TenantCreateRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;
    private final VapiAssistantProvisioner provisioner;
    private final RoleService roleService;

    @Transactional
    public Tenant create(TenantCreateRequest request) {
        Tenant tenant = Tenant.builder()
                .name(request.name())
                .subdomain(normalizeSubdomain(request.subdomain(), null))
                .phoneNumber(request.phoneNumber())
                .greetingText(request.greetingText())
                .workingHours(request.workingHours())
                .handoffNumber(request.handoffNumber())
                .languageConfig(request.languageConfig())
                .industry(request.industry() != null && !request.industry().isBlank() ? request.industry().trim() : "RENTAL")
                .sttDomain(request.sttDomain())
                .sttTopic(request.sttTopic())
                .sttVocabulary(request.sttVocabulary())
                .build();
        tenant = tenantRepository.save(tenant);

        // Its own copy of the owner role, so the first account has something to be assigned.
        // A copy rather than the shared template: this business may later narrow what its owner
        // can do, and that must not change every other business on the platform.
        roleService.createOwnerRoleFor(tenant.getId());

        return syncAssistant(tenant);
    }

    @Transactional(readOnly = true)
    public List<Tenant> list() {
        return tenantRepository.findAll();
    }

    /** Columns the admin table may sort by. Anything else falls back to {@link #DEFAULT_SORT}. */
    public static final Set<String> SORTABLE =
            Set.of("name", "subdomain", "phoneNumber", "monthlyFee", "createdAt");

    public static final String DEFAULT_SORT = "name";

    @Transactional(readOnly = true)
    public Page<Tenant> search(String query, Pageable pageable) {
        String q = query == null ? "" : query.trim().toLowerCase();
        return tenantRepository.search(q, pageable);
    }

    @Transactional(readOnly = true)
    public Tenant get(UUID id) {
        return tenantRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Tenant", id));
    }

    /**
     * Accepts either the UUID or the subdomain, so the admin panel can put a readable name in the
     * address bar instead of {@code 11111111-1111-1111-1111-111111111111}.
     *
     * <p>The two cannot collide: a subdomain must contain at least one letter-or-digit label and is
     * never 36 characters of hex with dashes in UUID positions, so parsing decides unambiguously.
     */
    @Transactional(readOnly = true)
    public Tenant getByIdOrSubdomain(String key) {
        if (key == null || key.isBlank()) {
            throw NotFoundException.of("Tenant", key);
        }
        try {
            return get(UUID.fromString(key.trim()));
        } catch (IllegalArgumentException notAUuid) {
            return tenantRepository.findBySubdomainIgnoreCase(key.trim().toLowerCase())
                    .orElseThrow(() -> NotFoundException.of("Tenant", key));
        }
    }

    @Transactional
    public Tenant updateConfig(UUID id, TenantConfigUpdateRequest request) {
        Tenant tenant = get(id);
        if (request.name() != null && !request.name().isBlank()) {
            tenant.setName(request.name().trim());
        }
        if (request.subdomain() != null) {
            tenant.setSubdomain(normalizeSubdomain(request.subdomain(), tenant.getId()));
        }
        if (request.phoneNumber() != null) tenant.setPhoneNumber(request.phoneNumber());
        if (request.greetingText() != null) tenant.setGreetingText(request.greetingText());
        if (request.workingHours() != null) tenant.setWorkingHours(request.workingHours());
        if (request.handoffNumber() != null) tenant.setHandoffNumber(request.handoffNumber());
        if (request.languageConfig() != null) tenant.setLanguageConfig(request.languageConfig());
        if (request.industry() != null && !request.industry().isBlank()) {
            tenant.setIndustry(request.industry().trim());
        }
        if (request.sttDomain() != null) tenant.setSttDomain(request.sttDomain());
        if (request.sttTopic() != null) tenant.setSttTopic(request.sttTopic());
        if (request.sttVocabulary() != null) tenant.setSttVocabulary(request.sttVocabulary());
        if (request.sttProvider() != null) tenant.setSttProvider(request.sttProvider());
        tenant = tenantRepository.save(tenant);
        // The greeting and the transcriber hints live inside Vapi too; leaving them stale would
        // make the panel disagree with what callers actually hear.
        return syncAssistant(tenant);
    }

    /**
     * Validates and claims a subdomain.
     *
     * <p>Checked here rather than left to the unique index so the operator gets "bu ünvan artıq
     * istifadə olunur" instead of a database constraint error, and so a blank value stays null
     * rather than colliding with every other tenant that has no panel address yet.
     */
    private String normalizeSubdomain(String raw, UUID selfId) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = Subdomains.normalizeOrThrow(raw);
        tenantRepository.findBySubdomainIgnoreCase(value).ifPresent(existing -> {
            if (!existing.getId().equals(selfId)) {
                throw new IllegalArgumentException(
                        "Bu ünvan artıq istifadə olunur: " + value + " (" + existing.getName() + ")");
            }
        });
        return value;
    }

    /**
     * Pushes the tenant into Vapi and remembers the assistant id.
     *
     * <p>Best-effort on purpose: a customer record must not fail to save because a third party is
     * unreachable. The tenant is left unprovisioned instead - a visible gap the admin panel shows
     * and a manual sync can close - rather than a lost record.
     */
    @Transactional
    public Tenant syncAssistant(Tenant tenant) {
        try {
            String assistantId = provisioner.provision(tenant);
            if (!assistantId.equals(tenant.getVapiAssistantId())) {
                tenant.setVapiAssistantId(assistantId);
                tenant = tenantRepository.save(tenant);
            }
        } catch (VapiAssistantProvisioner.ProvisioningException e) {
            log.error("Tenant {} saved, but its Vapi assistant could not be set up - it will not "
                    + "receive calls until this is synced: {}", tenant.getId(), e.getMessage());
        }
        return tenant;
    }

    /** Same, but surfaces the failure - used by the explicit sync action in the admin panel. */
    @Transactional
    public Tenant syncAssistantOrThrow(UUID id) {
        Tenant tenant = get(id);
        tenant.setVapiAssistantId(provisioner.provision(tenant));
        return tenantRepository.save(tenant);
    }

    /**
     * Re-pushes every tenant. Needed whenever a platform-wide setting changes - a new voice, a
     * retuned stability value - because in Vapi those live on each assistant separately.
     */
    @Transactional
    public int syncAllAssistants() {
        int synced = 0;
        for (Tenant tenant : tenantRepository.findAll()) {
            try {
                tenant.setVapiAssistantId(provisioner.provision(tenant));
                tenantRepository.save(tenant);
                synced++;
            } catch (VapiAssistantProvisioner.ProvisioningException e) {
                log.error("Could not sync tenant {}: {}", tenant.getId(), e.getMessage());
            }
        }
        return synced;
    }
}
