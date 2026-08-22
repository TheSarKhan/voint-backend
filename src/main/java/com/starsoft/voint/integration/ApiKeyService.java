package com.starsoft.voint.integration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.starsoft.voint.settings.SecretCipher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String KEY_PREFIX = "vk_live_";

    private final TenantApiKeyRepository apiKeyRepository;
    private final SecretCipher secretCipher;

    public record GeneratedApiKey(
            UUID id,
            String name,
            String rawApiKey,
            String keyPrefix,
            String permissions,
            Instant createdAt
    ) {}

    @Transactional(readOnly = true)
    public List<TenantApiKey> listKeys(UUID tenantId) {
        return apiKeyRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    @Transactional
    public GeneratedApiKey createKey(UUID tenantId, String name, String permissions) {
        if (name == null || name.isBlank()) {
            name = "1C / ERP İnteqrasiya Açar";
        }
        byte[] randomBytes = new byte[24];
        RANDOM.nextBytes(randomBytes);
        String randomSuffix = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        String rawApiKey = KEY_PREFIX + randomSuffix;

        String keyHash = hashKey(rawApiKey);
        String displayPrefix = rawApiKey.substring(0, Math.min(12, rawApiKey.length())) + "...";
        String encrypted = secretCipher.encrypt(rawApiKey);

        TenantApiKey apiKey = TenantApiKey.builder()
                .tenantId(tenantId)
                .name(name.trim())
                .keyHash(keyHash)
                .keyPrefix(displayPrefix)
                .keyEncrypted(encrypted)
                .permissions(permissions != null && !permissions.isBlank() ? permissions.trim() : "CATALOG_READ,CATALOG_WRITE")
                .active(true)
                .build();

        TenantApiKey saved = apiKeyRepository.save(apiKey);
        log.info("Generated new API Key '{}' ({}) for tenant {}", saved.getName(), saved.getKeyPrefix(), tenantId);

        return new GeneratedApiKey(
                saved.getId(),
                saved.getName(),
                rawApiKey,
                saved.getKeyPrefix(),
                saved.getPermissions(),
                saved.getCreatedAt()
        );
    }

    @Transactional
    public void revokeKey(UUID tenantId, UUID keyId) {
        TenantApiKey key = apiKeyRepository.findByTenantIdAndId(tenantId, keyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "API açarı tapılmadı"));
        apiKeyRepository.delete(key);
        log.info("Revoked API Key '{}' for tenant {}", key.getName(), tenantId);
    }

    @Transactional
    public Optional<TenantApiKey> validateKey(String rawApiKey) {
        if (rawApiKey == null || rawApiKey.isBlank()) {
            return Optional.empty();
        }
        String cleaned = rawApiKey.trim();
        if (cleaned.startsWith("Bearer ")) {
            cleaned = cleaned.substring(7).trim();
        }
        String keyHash = hashKey(cleaned);
        Optional<TenantApiKey> matched = apiKeyRepository.findByKeyHashAndActiveTrue(keyHash);
        matched.ifPresent(k -> {
            k.setLastUsedAt(Instant.now());
            apiKeyRepository.save(k);
        });
        return matched;
    }

    public static String hashKey(String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not supported", e);
        }
    }
}
