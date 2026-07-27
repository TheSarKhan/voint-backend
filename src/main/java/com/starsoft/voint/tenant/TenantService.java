package com.starsoft.voint.tenant;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.starsoft.voint.common.exception.NotFoundException;
import com.starsoft.voint.provisioning.VapiAssistantProvisioner;
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

    @Transactional
    public Tenant create(TenantCreateRequest request) {
        Tenant tenant = Tenant.builder()
                .name(request.name())
                .phoneNumber(request.phoneNumber())
                .greetingText(request.greetingText())
                .workingHours(request.workingHours())
                .handoffNumber(request.handoffNumber())
                .languageConfig(request.languageConfig())
                .sttDomain(request.sttDomain())
                .sttTopic(request.sttTopic())
                .sttVocabulary(request.sttVocabulary())
                .build();
        tenant = tenantRepository.save(tenant);
        return syncAssistant(tenant);
    }

    @Transactional(readOnly = true)
    public List<Tenant> list() {
        return tenantRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Tenant get(UUID id) {
        return tenantRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Tenant", id));
    }

    @Transactional
    public Tenant updateConfig(UUID id, TenantConfigUpdateRequest request) {
        Tenant tenant = get(id);
        if (request.phoneNumber() != null) tenant.setPhoneNumber(request.phoneNumber());
        if (request.greetingText() != null) tenant.setGreetingText(request.greetingText());
        if (request.workingHours() != null) tenant.setWorkingHours(request.workingHours());
        if (request.handoffNumber() != null) tenant.setHandoffNumber(request.handoffNumber());
        if (request.languageConfig() != null) tenant.setLanguageConfig(request.languageConfig());
        if (request.sttDomain() != null) tenant.setSttDomain(request.sttDomain());
        if (request.sttTopic() != null) tenant.setSttTopic(request.sttTopic());
        if (request.sttVocabulary() != null) tenant.setSttVocabulary(request.sttVocabulary());
        tenant = tenantRepository.save(tenant);
        // The greeting and the transcriber hints live inside Vapi too; leaving them stale would
        // make the panel disagree with what callers actually hear.
        return syncAssistant(tenant);
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
