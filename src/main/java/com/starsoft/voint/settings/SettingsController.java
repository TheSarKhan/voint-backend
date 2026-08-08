package com.starsoft.voint.settings;

import com.starsoft.voint.rbac.Permission;
import com.starsoft.voint.rbac.PublicEndpoint;
import com.starsoft.voint.rbac.RequirePermission;
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

    /** A bare domain: labels joined by dots, no scheme, no path, no leading or trailing dot. */
    private static final java.util.regex.Pattern DOMAIN = java.util.regex.Pattern.compile(
            "^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$");

    private static final java.util.regex.Pattern PORT = java.util.regex.Pattern.compile("^\\d{1,5}$");

    private final PlatformSettingsService settings;
    private final ProviderProbe probe;
    private final VapiSyncService vapiSync;
    private final ProviderHealthService providerHealth;
    private final TenantAccessGuard tenantAccessGuard;
    private final com.starsoft.voint.mail.MailService mailService;

    @RequirePermission(resource = Permission.Resource.PROVIDER, action = Permission.Action.READ, tenantScoped = false)
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

    @RequirePermission(resource = Permission.Resource.PROVIDER, action = Permission.Action.UPDATE, tenantScoped = false)
    @PutMapping("/api/v1/admin/settings/{key}")
    @Operation(summary = "Verify a credential against its provider, then store it (SUPER_ADMIN only)")
    public List<SettingView> update(@PathVariable String key,
                                    @Valid @RequestBody SettingUpdateRequest request) {
        tenantAccessGuard.requireSuperAdmin();
        SettingKey settingKey = parse(key);
        String value = request.value().trim();

        verifyOrReject(settingKey, value);

        // Remember what to go back to: if the change cannot be propagated to Vapi we must not
        // keep it here either.
        boolean hadOwnValue = settings.isSetInDatabase(settingKey);
        String previous = settings.get(settingKey);

        settings.set(settingKey, value, currentUserEmail());

        // Vapi holds its own copy of the ElevenLabs settings and does the actual synthesis. If the
        // push fails we roll back, because the alternative is the worst possible state: the panel
        // and the monitor both check the NEW key and report healthy, while every call still runs
        // on the OLD key inside Vapi and fails.
        if (request.shouldSync() && requiresVapi(settingKey)) {
            try {
                String result = settingKey == SettingKey.ELEVENLABS_API_KEY
                        ? vapiSync.syncElevenLabsKey()
                        : vapiSync.syncVoiceId();
                log.info("{} saved - {}", settingKey.getKey(), result);
            } catch (VapiSyncService.VapiSyncException e) {
                rollback(settingKey, hadOwnValue, previous);
                // 422, not 502: Cloudflare replaces an origin 502 with its own error page and this
                // explanation - the one thing the operator needs - never arrives. See
                // GlobalExceptionHandler#handleUpstream.
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Vapi-yə ötürülmədi, ona görə dəyişiklik geri alındı: " + e.getMessage(), e);
            }
        }

        providerHealth.refresh();
        return list();
    }

    /**
     * The one deliberate hole in "secrets are fingerprinted, not shown" - an operator sometimes
     * genuinely needs the plaintext (to paste into another tool, or confirm prod and local match).
     * Kept narrow: SUPER_ADMIN only, and every reveal is logged so there is a trail of who saw what,
     * when.
     */
    @RequirePermission(resource = Permission.Resource.PROVIDER, action = Permission.Action.READ, tenantScoped = false)
    @GetMapping("/api/v1/admin/settings/{key}/reveal")
    @Operation(summary = "Full plaintext value of a credential (SUPER_ADMIN only, logged)")
    public Map<String, String> reveal(@PathVariable String key) {
        tenantAccessGuard.requireSuperAdmin();
        SettingKey settingKey = parse(key);
        String value = settings.get(settingKey);
        if (value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Bu ayar hələ təyin olunmayıb");
        }
        log.warn("Provider credential '{}' revealed by {}", settingKey.getKey(), currentUserEmail());
        return Map.of("value", value);
    }

    @RequirePermission(resource = Permission.Resource.PROVIDER, action = Permission.Action.UPDATE, tenantScoped = false)
    @DeleteMapping("/api/v1/admin/settings/{key}")
    @Operation(summary = "Fall a credential back to the server configuration (SUPER_ADMIN only)")
    public List<SettingView> clear(@PathVariable String key) {
        tenantAccessGuard.requireSuperAdmin();
        settings.clear(parse(key), currentUserEmail());
        providerHealth.refresh();
        return list();
    }

    /**
     * Actually sends a message. Saving the five SMTP fields proves nothing - the server can
     * still refuse the credentials, the sender address can be unverified, or the port can be
     * blocked - and the first person to discover that should not be a new customer waiting for
     * a login that never arrives.
     */
    @RequirePermission(resource = Permission.Resource.PROVIDER, action = Permission.Action.UPDATE,
            tenantScoped = false)
    @PostMapping("/api/v1/admin/settings/test-email")
    @Operation(summary = "Send a test email to the given address")
    public Map<String, String> testEmail(@RequestBody Map<String, String> body) {
        tenantAccessGuard.requireSuperAdmin();
        String to = body.get("to");
        if (to == null || !to.contains("@")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Düzgün e-poçt ünvanı yazın");
        }
        mailService.send(to, "Voint - test mesajı", com.starsoft.voint.mail.MailTemplates.test());
        return Map.of("sent", to);
    }

    @RequirePermission(resource = Permission.Resource.PROVIDER, action = Permission.Action.READ, tenantScoped = false)
    @PostMapping("/api/v1/admin/settings/recheck")
    @Operation(summary = "Re-probe every provider now instead of waiting for the timer")
    public List<ProviderHealth> recheck() {
        tenantAccessGuard.requireSuperAdmin();
        return providerHealth.refresh();
    }

    /** Only the ElevenLabs settings have a second copy inside Vapi that can drift out of sync. */
    private boolean requiresVapi(SettingKey key) {
        return key == SettingKey.ELEVENLABS_API_KEY || key == SettingKey.ELEVENLABS_VOICE_ID;
    }

    private void rollback(SettingKey key, boolean hadOwnValue, String previous) {
        if (hadOwnValue) {
            settings.set(key, previous, currentUserEmail());
        } else {
            // There was no panel value before - go back to the server configuration.
            settings.clear(key, currentUserEmail());
        }
        log.warn("Rolled back '{}' after a failed Vapi sync", key.getKey());
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
            // An SMTP field cannot be verified on its own - the server only accepts the five
            // together - so only the shape is checked here. The real test is the "Test e-poçtu"
            // button, which actually sends one.
            case SMTP_HOST, SMTP_USERNAME, SMTP_PASSWORD ->
                    new ProviderProbe.Result(true, "Yadda saxlanıldı");
            case SMTP_PORT -> PORT.matcher(value).matches()
                    ? new ProviderProbe.Result(true, "Port qəbul edildi")
                    : new ProviderProbe.Result(false, "Port yalnız rəqəm ola bilər, məsələn 587");
            case SMTP_FROM -> value.contains("@")
                    ? new ProviderProbe.Result(true, "Ünvan qəbul edildi")
                    : new ProviderProbe.Result(false,
                            "E-poçt ünvanı olmalıdır, məsələn: Voint <panel@voint.az>");
            // Nothing to probe - a domain is not a credential. Only reject shapes that would
            // silently produce broken addresses, like a pasted URL or a leading dot.
            case PANEL_DOMAIN -> DOMAIN.matcher(value).matches()
                    ? new ProviderProbe.Result(true, "Domen qəbul edildi")
                    : new ProviderProbe.Result(false,
                            "Yalnız domen yazın - protokol və əyri xətt olmadan. Məsələn: voint.az");
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
