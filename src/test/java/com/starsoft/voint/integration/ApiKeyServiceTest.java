package com.starsoft.voint.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class ApiKeyServiceTest {

    @Autowired
    private ApiKeyService apiKeyService;

    private static final UUID CES_TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void testApiKeyLifecycle() {
        ApiKeyService.GeneratedApiKey generated = apiKeyService.createKey(
                CES_TENANT_ID,
                "1C Anbar Sinxronizasiyası",
                "CATALOG_READ,CATALOG_WRITE"
        );

        assertNotNull(generated.id());
        assertNotNull(generated.rawApiKey());
        assertTrue(generated.rawApiKey().startsWith("vk_live_"));

        // Validate key
        Optional<TenantApiKey> valid = apiKeyService.validateKey(generated.rawApiKey());
        assertTrue(valid.isPresent());
        assertEquals(CES_TENANT_ID, valid.get().getTenantId());
        assertEquals("1C Anbar Sinxronizasiyası", valid.get().getName());

        // Validate with Bearer prefix
        Optional<TenantApiKey> validBearer = apiKeyService.validateKey("Bearer " + generated.rawApiKey());
        assertTrue(validBearer.isPresent());

        // Invalid key
        Optional<TenantApiKey> invalid = apiKeyService.validateKey("vk_live_invalidkey123");
        assertFalse(invalid.isPresent());

        // Revoke
        apiKeyService.revokeKey(CES_TENANT_ID, generated.id());
        Optional<TenantApiKey> afterRevoke = apiKeyService.validateKey(generated.rawApiKey());
        assertFalse(afterRevoke.isPresent());
    }
}
