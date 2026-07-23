package com.starsoft.voint.voice;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.starsoft.voint.call.Call;
import com.starsoft.voint.call.CallRepository;
import com.starsoft.voint.call.CallStatus;
import com.starsoft.voint.crm.CallTranscript;
import com.starsoft.voint.crm.CallTranscriptRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles Vapi's assistant-level "server" webhook (POST /api/v1/voice/events - NOT the custom-LLM
 * completions endpoint). This is where Vapi reports call lifecycle events; the only one we act on
 * right now is "end-of-call-report", which carries the full transcript + AI summary once a call
 * ends. Every request body is wrapped as {@code {"message": {...}}} per Vapi's convention.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VapiEventService {

    /** Bootstrap-stage fallback tenant (CES, seeded by V2__seed.sql) - matches VoiceWebhookService. */
    private static final UUID DEFAULT_TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final String END_OF_CALL_REPORT = "end-of-call-report";

    private final CallRepository callRepository;
    private final CallTranscriptRepository callTranscriptRepository;

    @Transactional
    public void handle(JsonNode body) {
        JsonNode message = body != null ? body.path("message") : null;
        if (message == null || message.isMissingNode()) {
            log.debug("Vapi event with no 'message' envelope - ignoring: {}", body);
            return;
        }

        String type = message.path("type").asText(null);
        if (!END_OF_CALL_REPORT.equals(type)) {
            // Other event types (status-update, transcript, speech-update, ...) - not handled yet.
            log.debug("Ignoring Vapi server event of type '{}'", type);
            return;
        }

        recordEndOfCall(message);
    }

    private void recordEndOfCall(JsonNode message) {
        UUID tenantId = resolveTenantId(message);
        String callerNumber = message.path("call").path("customer").path("number").asText(null);
        Instant startedAt = parseInstant(message.path("startedAt").asText(null));
        Instant endedAt = parseInstant(message.path("endedAt").asText(null));
        String endedReason = message.path("endedReason").asText(null);
        String transcript = message.path("artifact").path("transcript").asText(null);
        String summary = message.path("analysis").path("summary").asText(null);

        Call call = Call.builder()
                .tenantId(tenantId)
                .callerNumber(callerNumber)
                .status(mapStatus(endedReason))
                .durationSeconds(computeDurationSeconds(startedAt, endedAt))
                .startedAt(startedAt != null ? startedAt : Instant.now())
                .endedAt(endedAt)
                .build();
        call = callRepository.save(call);

        if (transcript != null || summary != null) {
            CallTranscript record = CallTranscript.builder()
                    .callId(call.getId())
                    .fullTranscript(transcript)
                    .aiSummary(summary)
                    .build();
            callTranscriptRepository.save(record);
        }

        log.info("Recorded end-of-call-report: call {} (tenant {}, caller {}, {}s, reason={})",
                call.getId(), tenantId, callerNumber, call.getDurationSeconds(), endedReason);
    }

    /**
     * BOOTSTRAP STAGE: same fallback as VoiceWebhookService - looks for
     * message.call.metadata.tenantId/tenant_id, else the seeded CES tenant.
     * TODO (later stage): real multi-tenant routing by the caller's dialed phone number.
     */
    private UUID resolveTenantId(JsonNode message) {
        JsonNode metadata = message.path("call").path("metadata");
        String raw = metadata.path("tenantId").asText(null);
        if (raw == null || raw.isBlank()) {
            raw = metadata.path("tenant_id").asText(null);
        }
        if (raw == null || raw.isBlank()) {
            log.warn("No tenant metadata on end-of-call-report - falling back to the bootstrap default "
                    + "CES tenant {}", DEFAULT_TENANT_ID);
            return DEFAULT_TENANT_ID;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            log.warn("call.metadata tenantId '{}' is not a valid UUID - falling back to CES tenant {}",
                    raw, DEFAULT_TENANT_ID);
            return DEFAULT_TENANT_ID;
        }
    }

    /** Heuristic: anything mentioning a transfer/forward counts as a human handoff, else resolved. */
    private CallStatus mapStatus(String endedReason) {
        if (endedReason == null) {
            return CallStatus.RESOLVED;
        }
        String lower = endedReason.toLowerCase();
        return (lower.contains("transfer") || lower.contains("forward")) ? CallStatus.HANDOFF : CallStatus.RESOLVED;
    }

    private Integer computeDurationSeconds(Instant startedAt, Instant endedAt) {
        if (startedAt == null || endedAt == null) {
            return null;
        }
        return (int) Duration.between(startedAt, endedAt).getSeconds();
    }

    private Instant parseInstant(String iso) {
        if (iso == null || iso.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(iso);
        } catch (Exception e) {
            log.warn("Could not parse Vapi timestamp '{}'", iso);
            return null;
        }
    }
}
