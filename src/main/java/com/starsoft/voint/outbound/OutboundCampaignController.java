package com.starsoft.voint.outbound;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.starsoft.voint.auth.TenantAccessGuard;
import com.starsoft.voint.outbound.dto.OutboundCampaignCreateRequest;
import com.starsoft.voint.outbound.dto.OutboundCampaignResponse;
import com.starsoft.voint.outbound.dto.OutboundCampaignUpdateRequest;
import com.starsoft.voint.outbound.dto.OutboundContactAddRequest;
import com.starsoft.voint.outbound.dto.OutboundContactResponse;
import com.starsoft.voint.rbac.Permission;
import com.starsoft.voint.rbac.RequirePermission;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/campaigns")
@RequiredArgsConstructor
@Tag(name = "Outbound Campaigns", description = "Outbound AI voice calling campaigns management")
public class OutboundCampaignController {

    private final OutboundCampaignService campaignService;
    private final TenantAccessGuard tenantAccessGuard;

    @RequirePermission(resource = Permission.Resource.CAMPAIGN, action = Permission.Action.READ)
    @GetMapping
    @Operation(summary = "List all outbound campaigns for tenant")
    public List<OutboundCampaignResponse> list(@PathVariable UUID tenantId) {
        tenantAccessGuard.requireAccess(tenantId);
        return campaignService.listCampaigns(tenantId).stream()
                .map(OutboundCampaignResponse::from)
                .toList();
    }

    @RequirePermission(resource = Permission.Resource.CAMPAIGN, action = Permission.Action.READ)
    @GetMapping("/{id}")
    @Operation(summary = "Get single outbound campaign")
    public OutboundCampaignResponse get(@PathVariable UUID tenantId, @PathVariable UUID id) {
        tenantAccessGuard.requireAccess(tenantId);
        return OutboundCampaignResponse.from(campaignService.getCampaign(tenantId, id));
    }

    @RequirePermission(resource = Permission.Resource.CAMPAIGN, action = Permission.Action.CREATE)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new outbound calling campaign")
    public OutboundCampaignResponse create(@PathVariable UUID tenantId,
                                           @Valid @RequestBody OutboundCampaignCreateRequest request) {
        tenantAccessGuard.requireAccess(tenantId);
        return OutboundCampaignResponse.from(campaignService.createCampaign(tenantId, request));
    }

    @RequirePermission(resource = Permission.Resource.CAMPAIGN, action = Permission.Action.UPDATE)
    @PutMapping("/{id}")
    @Operation(summary = "Update an outbound campaign")
    public OutboundCampaignResponse update(@PathVariable UUID tenantId,
                                           @PathVariable UUID id,
                                           @RequestBody OutboundCampaignUpdateRequest request) {
        tenantAccessGuard.requireAccess(tenantId);
        return OutboundCampaignResponse.from(campaignService.updateCampaign(tenantId, id, request));
    }

    @RequirePermission(resource = Permission.Resource.CAMPAIGN, action = Permission.Action.DELETE)
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an outbound campaign")
    public void delete(@PathVariable UUID tenantId, @PathVariable UUID id) {
        tenantAccessGuard.requireAccess(tenantId);
        campaignService.deleteCampaign(tenantId, id);
    }

    @RequirePermission(resource = Permission.Resource.CAMPAIGN, action = Permission.Action.UPDATE)
    @PostMapping("/{id}/start")
    @Operation(summary = "Start / Resume campaign calls")
    public OutboundCampaignResponse start(@PathVariable UUID tenantId, @PathVariable UUID id) {
        tenantAccessGuard.requireAccess(tenantId);
        return OutboundCampaignResponse.from(campaignService.startCampaign(tenantId, id));
    }

    @RequirePermission(resource = Permission.Resource.CAMPAIGN, action = Permission.Action.UPDATE)
    @PostMapping("/{id}/pause")
    @Operation(summary = "Pause campaign calls")
    public OutboundCampaignResponse pause(@PathVariable UUID tenantId, @PathVariable UUID id) {
        tenantAccessGuard.requireAccess(tenantId);
        return OutboundCampaignResponse.from(campaignService.pauseCampaign(tenantId, id));
    }

    @RequirePermission(resource = Permission.Resource.CAMPAIGN, action = Permission.Action.CREATE)
    @PostMapping("/{id}/contacts")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add contact numbers to campaign (JSON)")
    public List<OutboundContactResponse> addContacts(@PathVariable UUID tenantId,
                                                    @PathVariable UUID id,
                                                    @RequestBody List<OutboundContactAddRequest> requests) {
        tenantAccessGuard.requireAccess(tenantId);
        return campaignService.addContacts(tenantId, id, requests).stream()
                .map(OutboundContactResponse::from)
                .toList();
    }

    @RequirePermission(resource = Permission.Resource.CAMPAIGN, action = Permission.Action.CREATE)
    @PostMapping(value = "/{id}/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import contact list from Excel or CSV file")
    public List<OutboundContactResponse> importFile(@PathVariable UUID tenantId,
                                                   @PathVariable UUID id,
                                                   @RequestParam("file") MultipartFile file) {
        tenantAccessGuard.requireAccess(tenantId);
        return campaignService.importContactsFromFile(tenantId, id, file).stream()
                .map(OutboundContactResponse::from)
                .toList();
    }

    @RequirePermission(resource = Permission.Resource.CAMPAIGN, action = Permission.Action.READ)
    @GetMapping("/{id}/contacts")
    @Operation(summary = "List paginated contacts in campaign")
    public Page<OutboundContactResponse> getContacts(@PathVariable UUID tenantId,
                                                    @PathVariable UUID id,
                                                    Pageable pageable) {
        tenantAccessGuard.requireAccess(tenantId);
        return campaignService.getContacts(tenantId, id, pageable)
                .map(OutboundContactResponse::from);
    }

    @RequirePermission(resource = Permission.Resource.CAMPAIGN, action = Permission.Action.UPDATE)
    @PatchMapping("/contacts/{contactId}/status")
    @Operation(summary = "Update contact status and call outcome")
    public OutboundContactResponse updateStatus(@PathVariable UUID tenantId,
                                                @PathVariable UUID contactId,
                                                @RequestBody Map<String, String> body) {
        tenantAccessGuard.requireAccess(tenantId);
        return OutboundContactResponse.from(campaignService.updateContactStatus(
                tenantId,
                contactId,
                body.get("status"),
                body.get("callOutcome"),
                body.get("notes")
        ));
    }
}
