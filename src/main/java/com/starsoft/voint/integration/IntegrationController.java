package com.starsoft.voint.integration;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.starsoft.voint.auth.TenantAccessGuard;
import com.starsoft.voint.integration.dto.IntegrationDtos.ApiKeyCreateRequest;
import com.starsoft.voint.integration.dto.IntegrationDtos.ApiKeyCreatedResponse;
import com.starsoft.voint.integration.dto.IntegrationDtos.ApiKeyResponse;
import com.starsoft.voint.integration.dto.IntegrationDtos.WebhookResponse;
import com.starsoft.voint.integration.dto.IntegrationDtos.WebhookUpdateRequest;
import com.starsoft.voint.rbac.Permission;
import com.starsoft.voint.rbac.RequirePermission;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/tenants/{id}/integrations")
@RequiredArgsConstructor
@Tag(name = "Integrations", description = "1C/ERP API Açarları və Webhook İdarəetməsi")
public class IntegrationController {

    private final ApiKeyService apiKeyService;
    private final TenantWebhookRepository webhookRepository;
    private final TenantAccessGuard tenantAccessGuard;

    @RequirePermission(resource = Permission.Resource.SETTINGS, action = Permission.Action.READ)
    @GetMapping("/keys")
    @Operation(summary = "Müəssisənin bütün API açarlarını siyahıla")
    public List<ApiKeyResponse> listKeys(@PathVariable("id") UUID tenantId) {
        tenantAccessGuard.requireAccess(tenantId);
        return apiKeyService.listKeys(tenantId).stream().map(ApiKeyResponse::from).toList();
    }

    @RequirePermission(resource = Permission.Resource.SETTINGS, action = Permission.Action.CREATE)
    @PostMapping("/keys")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Yeni 1C / ERP API açarı yarat")
    public ApiKeyCreatedResponse createKey(@PathVariable("id") UUID tenantId,
                                           @RequestBody(required = false) ApiKeyCreateRequest request) {
        tenantAccessGuard.requireAccess(tenantId);
        String name = request != null ? request.name() : "1C İnteqrasiya Açar";
        String perms = request != null ? request.permissions() : "CATALOG_READ,CATALOG_WRITE";
        ApiKeyService.GeneratedApiKey gen = apiKeyService.createKey(tenantId, name, perms);
        return new ApiKeyCreatedResponse(
                gen.id(),
                gen.name(),
                gen.rawApiKey(),
                gen.keyPrefix(),
                gen.permissions(),
                gen.createdAt()
        );
    }

    @RequirePermission(resource = Permission.Resource.SETTINGS, action = Permission.Action.DELETE)
    @DeleteMapping("/keys/{keyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "API açarını ləğv et")
    public void revokeKey(@PathVariable("id") UUID tenantId, @PathVariable UUID keyId) {
        tenantAccessGuard.requireAccess(tenantId);
        apiKeyService.revokeKey(tenantId, keyId);
    }

    @RequirePermission(resource = Permission.Resource.SETTINGS, action = Permission.Action.READ)
    @GetMapping("/webhook")
    @Operation(summary = "Müəssisənin Webhook sazlamalarını gətir")
    public WebhookResponse getWebhook(@PathVariable("id") UUID tenantId) {
        tenantAccessGuard.requireAccess(tenantId);
        return webhookRepository.findFirstByTenantIdAndActiveTrue(tenantId)
                .map(WebhookResponse::from)
                .orElse(null);
    }

    @RequirePermission(resource = Permission.Resource.SETTINGS, action = Permission.Action.UPDATE)
    @PutMapping("/webhook")
    @Operation(summary = "Müəssisənin Webhook sazlamalarını yenilə")
    public WebhookResponse updateWebhook(@PathVariable("id") UUID tenantId,
                                         @RequestBody WebhookUpdateRequest request) {
        tenantAccessGuard.requireAccess(tenantId);
        TenantWebhook webhook = webhookRepository.findFirstByTenantIdAndActiveTrue(tenantId)
                .orElse(TenantWebhook.builder().tenantId(tenantId).build());

        if (request.url() != null) webhook.setUrl(request.url().trim());
        if (request.secret() != null) webhook.setSecret(request.secret().trim());
        if (request.eventTypes() != null) webhook.setEventTypes(request.eventTypes().trim());
        if (request.active() != null) webhook.setActive(request.active());

        return WebhookResponse.from(webhookRepository.save(webhook));
    }
}
