package com.starsoft.voint.call;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.starsoft.voint.auth.TenantAccessGuard;
import com.starsoft.voint.call.dto.CallCreateRequest;
import com.starsoft.voint.call.dto.CallDetailResponse;
import com.starsoft.voint.call.dto.CallResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/tenants/{id}/calls")
@RequiredArgsConstructor
@Tag(name = "Calls", description = "Call journal (calls handled by the AI agent)")
public class CallController {

    private final CallService callService;
    private final TenantAccessGuard tenantAccessGuard;

    @GetMapping
    @Operation(summary = "List calls of the tenant")
    public List<CallResponse> list(@PathVariable("id") UUID tenantId) {
        tenantAccessGuard.requireAccess(tenantId);
        return callService.list(tenantId).stream().map(CallResponse::from).toList();
    }

    @GetMapping("/{callId}")
    @Operation(summary = "Get a single call, including its transcript + AI summary when available")
    public CallDetailResponse get(@PathVariable("id") UUID tenantId, @PathVariable UUID callId) {
        tenantAccessGuard.requireAccess(tenantId);
        Call call = callService.get(tenantId, callId);
        return CallDetailResponse.from(call, callService.getTranscript(call.getId()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a call record manually (testing; later fed by Vapi call events)")
    public CallResponse create(@PathVariable("id") UUID tenantId,
                               @RequestBody CallCreateRequest request) {
        tenantAccessGuard.requireAccess(tenantId);
        return CallResponse.from(callService.create(tenantId, request));
    }
}
