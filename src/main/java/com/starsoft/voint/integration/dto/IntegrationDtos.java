package com.starsoft.voint.integration.dto;

import java.time.Instant;
import java.util.UUID;

import com.starsoft.voint.integration.TenantApiKey;
import com.starsoft.voint.integration.TenantWebhook;

public final class IntegrationDtos {

    private IntegrationDtos() {}

    public record ApiKeyResponse(
            UUID id,
            UUID tenantId,
            String name,
            String keyPrefix,
            String permissions,
            Instant lastUsedAt,
            Instant createdAt,
            boolean active
    ) {
        public static ApiKeyResponse from(TenantApiKey key) {
            return new ApiKeyResponse(
                    key.getId(),
                    key.getTenantId(),
                    key.getName(),
                    key.getKeyPrefix(),
                    key.getPermissions(),
                    key.getLastUsedAt(),
                    key.getCreatedAt(),
                    key.isActive()
            );
        }
    }

    public record ApiKeyCreateRequest(
            String name,
            String permissions
    ) {}

    public record ApiKeyCreatedResponse(
            UUID id,
            String name,
            String rawApiKey,
            String keyPrefix,
            String permissions,
            Instant createdAt
    ) {}

    public record WebhookResponse(
            UUID id,
            UUID tenantId,
            String url,
            String secret,
            String eventTypes,
            boolean active,
            Instant createdAt
    ) {
        public static WebhookResponse from(TenantWebhook w) {
            return new WebhookResponse(
                    w.getId(),
                    w.getTenantId(),
                    w.getUrl(),
                    w.getSecret() != null ? "••••••••" : null,
                    w.getEventTypes(),
                    w.isActive(),
                    w.getCreatedAt()
            );
        }
    }

    public record WebhookUpdateRequest(
            String url,
            String secret,
            String eventTypes,
            Boolean active
    ) {}
}
