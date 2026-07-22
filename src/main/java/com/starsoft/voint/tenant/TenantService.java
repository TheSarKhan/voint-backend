package com.starsoft.voint.tenant;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.starsoft.voint.common.exception.NotFoundException;
import com.starsoft.voint.tenant.dto.TenantConfigUpdateRequest;
import com.starsoft.voint.tenant.dto.TenantCreateRequest;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;

    @Transactional
    public Tenant create(TenantCreateRequest request) {
        Tenant tenant = Tenant.builder()
                .name(request.name())
                .phoneNumber(request.phoneNumber())
                .greetingText(request.greetingText())
                .workingHours(request.workingHours())
                .handoffNumber(request.handoffNumber())
                .languageConfig(request.languageConfig())
                .build();
        return tenantRepository.save(tenant);
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
        return tenantRepository.save(tenant);
    }
}
