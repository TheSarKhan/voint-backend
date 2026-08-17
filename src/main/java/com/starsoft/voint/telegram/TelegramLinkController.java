package com.starsoft.voint.telegram;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.starsoft.voint.auth.TenantAccessGuard;
import com.starsoft.voint.rbac.Permission;
import com.starsoft.voint.rbac.RequirePermission;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** Lets a tenant link its own Telegram chats and see/remove what is already linked. */
@RestController
@RequestMapping("/api/v1/tenants/{id}/telegram")
@RequiredArgsConstructor
@Tag(name = "Telegram", description = "Per-tenant call notifications over Telegram")
public class TelegramLinkController {

    private final TelegramLinkService linkService;
    private final TelegramChatRepository chats;
    private final TenantAccessGuard tenantAccessGuard;

    @RequirePermission(resource = Permission.Resource.SETTINGS, action = Permission.Action.READ)
    @PostMapping("/link")
    @Operation(summary = "One-time link (t.me deep link, 15 min) to connect a new Telegram chat")
    public LinkResponse createLink(@PathVariable("id") UUID tenantId) {
        tenantAccessGuard.requireAccess(tenantId);
        String token = linkService.createToken(tenantId);
        String deepLink = linkService.buildDeepLink(token);
        if (deepLink == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Telegram bot hələ qurulmayıb - platform administratoru ilə əlaqə saxlayın");
        }
        return new LinkResponse(deepLink);
    }

    @RequirePermission(resource = Permission.Resource.SETTINGS, action = Permission.Action.READ)
    @GetMapping("/chats")
    @Operation(summary = "Chats currently linked for this tenant")
    public List<ChatResponse> list(@PathVariable("id") UUID tenantId) {
        tenantAccessGuard.requireAccess(tenantId);
        return chats.findByTenantIdOrderByLinkedAtDesc(tenantId).stream()
                .map(c -> new ChatResponse(c.getId(), c.getLabel(), c.getLinkedAt()))
                .toList();
    }

    @RequirePermission(resource = Permission.Resource.SETTINGS, action = Permission.Action.UPDATE)
    @DeleteMapping("/chats/{chatId}")
    @Operation(summary = "Unlink a chat - it stops receiving call notifications")
    public void unlink(@PathVariable("id") UUID tenantId, @PathVariable UUID chatId) {
        tenantAccessGuard.requireAccess(tenantId);
        chats.findByIdAndTenantId(chatId, tenantId).ifPresent(chats::delete);
    }

    public record LinkResponse(String deepLink) {
    }

    public record ChatResponse(UUID id, String label, java.time.Instant linkedAt) {
    }
}
