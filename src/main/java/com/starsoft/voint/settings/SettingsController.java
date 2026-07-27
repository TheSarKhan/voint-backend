package com.starsoft.voint.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.starsoft.voint.auth.TenantAccessGuard;
import com.starsoft.voint.health.ProviderHealth;
import com.starsoft.voint.health.ProviderHealthService;
import com.starsoft.voint.health.ProviderProbe;
import com.starsoft.voint.settings.dto.SettingUpdateRequest;
import com.starsoft.voint.settings.dto.SettingView;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Lets a platform operator manage provider credentials from the admin panel.
 *
 * <p>The write path is deliberately strict: a credential is probed against its provider first and
 * only stored if it works. Storing an unverified key would just move the original failure - a key
 * that looks saved but breaks calls - from the server into the product.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Platform settings", description = "Provider credentials, managed from the admin panel")
public class SettingsController {

    private final PlatformSettingsService settings;
    private final ProviderProbe probe;
    private final VapiSyncService vapiSync;
    private final ProviderHealthService providerHealth;
    private final TenantAccessGuard tenantAccessGuard;

    @GetMapping("/api/v1/admin/settings")
    @Operation(summary = "All credentials, masked (SUPER_ADMIN only)")
    public List<SettingView> list() {
        tenantAccessGuard.requireSuperAdmin();
        Map<SettingKey, PlatformSetting> meta = settings.metadata();

        List<SettingView> out = new ArrayList<>();
        for (SettingKey key : SettingKey.values()) {
            String effective = settings.get(key);
            PlatformSetting row = meta.get(key);
            out.add(new SettingView(
                    key.getKey(),
                    key.getLabel(),
                    key.getDescription(),
                    key.isSecret(),
                    !effective.isBlank(),
                    row != null,
                    hint(key, effective),
                    row != null ? row.getUpdatedAt() : null,
                    row != null ? row.getUpdatedBy() : null));
        }
        return out;
    }

    @PutMapping("/api/v1/admin/settings/{key}")
    @Operation(summary = "Verify a credential against its provider, then store it (SUPER_ADMIN only)")
    public List<SettingView> update(@PathVariable String key,
                                    @Valid @RequestBody SettingUpdateRequest request) {
        tenantAccessGuard.requireSuperAdmin();
        SettingKey settingKey = parse(key);
        String value = request.value().trim();

        verifyOrReject(settingKey, value);
        settings.set(settingKey, value, currentUserEmail());

        // Vapi holds its own copy of the ElevenLabs settings and does the actual synthesis, so a
        // save that stops here would leave calls broken while the panel looked healthy.
        if (request.shouldSync()) {
            try {
                if (settingKey == SettingKey.ELEVENLABS_API_KEY) {
                    log.info("ElevenLabs key saved - {}", vapiSync.syncElevenLabsKey());
                } else if (settingKey == SettingKey.ELEVENLABS_VOICE_ID) {
                    log.info("ElevenLabs voice saved - {}", vapiSync.syncVoiceId());
                }
            } catch (VapiSyncService.VapiSyncException e) {
                // Saved here but not in Vapi: say so plainly rather than reporting success.
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Açar yadda saxlanıldı, amma Vapi-yə ötürülmədi: " + e.getMessage(), e);
            }
        }

        providerHealth.refresh();
        return list();
    }

    @DeleteMapping("/api/v1/admin/settings/{key}")
    @Operation(summary = "Fall a credential back to the server configuration (SUPER_ADMIN only)")
    public List<SettingView> clear(@PathVariable String key) {
        tenantAccessGuard.requireSuperAdmin();
        settings.clear(parse(key), currentUserEmail());
        providerHealth.refresh();
        return list();
    }

    @PostMapping("/api/v1/admin/settings/recheck")
    @Operation(summary = "Re-probe every provider now instead of waiting for the timer")
    public List<ProviderHealth> recheck() {
        tenantAccessGuard.requireSuperAdmin();
        return providerHealth.refresh();
    }

    /** Probes the candidate value; a credential that does not work never reaches the database. */
    private void verifyOrReject(SettingKey key, String value) {
        ProviderProbe.Result result = switch (key) {
            case ELEVENLABS_API_KEY ->
                    probe.elevenLabs(value, settings.get(SettingKey.ELEVENLABS_VOICE_ID));
            case ELEVENLABS_VOICE_ID ->
                    probe.elevenLabs(settings.get(SettingKey.ELEVENLABS_API_KEY), value);
            case GEMINI_API_KEY -> probe.gemini(value);
            case VAPI_PRIVATE_KEY -> probe.vapi(value);
        };
        if (!result.ok()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, result.detail());
        }
    }

    /** Secrets are fingerprinted, not shown; non-secrets (the voice id) are safe to display. */
    private String hint(SettingKey key, String value) {
        if (value.isBlank()) {
            return null;
        }
        if (!key.isSecret()) {
            return value;
        }
        return value.length() <= 4 ? "…" : "…" + value.substring(value.length() - 4);
    }

    private SettingKey parse(String key) {
        try {
            return SettingKey.fromKey(key);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Belə ayar yoxdur: " + key);
        }
    }

    private String currentUserEmail() {
        var authentication = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "unknown";
    }
}
