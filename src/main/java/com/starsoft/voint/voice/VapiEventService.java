package com.starsoft.voint.voice;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.fasterxml.jackson.databind.JsonNode;
import com.starsoft.voint.call.Call;
import com.starsoft.voint.call.CallRepository;
import com.starsoft.voint.call.CallStatus;
import com.starsoft.voint.crm.CallTranscript;
import com.starsoft.voint.crm.CallTranscriptRepository;
import com.starsoft.voint.crm.CustomerService;
import com.starsoft.voint.question.CallAnalysisService;
import com.starsoft.voint.tenant.Tenant;
import com.starsoft.voint.tenant.TenantRepository;

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
    private final CallAnalysisService callAnalysisService;
    private final CustomerService customerService;
    private final CallConcurrencyService callConcurrencyService;
    private final TenantRepository tenantRepository;

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
        String language = resolveLanguage(message);

        Call call = Call.builder()
                .tenantId(tenantId)
                .callerNumber(callerNumber)
                .languageDetected(language)
                .status(mapStatus(endedReason))
                .durationSeconds(computeDurationSeconds(startedAt, endedAt))
                .startedAt(startedAt != null ? startedAt : Instant.now())
                .endedAt(endedAt)
                .build();
        call = callRepository.save(call);
        callConcurrencyService.release(tenantId, message.path("call").path("id").asText(null));

        // Təkrar zəng edən müştərini tanı: kart yoxdursa açılır, varsa "son görülmə" təzələnir.
        // Nömrəsiz zəng (test/veb zəngləri, bootstrap mərhələsi) kart açmır - telefon xətti
        // qoşulmayana qədər callerNumber boş gəlir.
        if (callerNumber != null && !callerNumber.isBlank()) {
            customerService.findOrCreateByPhone(tenantId, callerNumber);
        }

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

        scheduleAnalysis(tenantId, call.getId(), transcript);
    }

    /**
     * Hands the call to the unanswered-question analysis - but only AFTER this transaction commits.
     *
     * <p>The analysis runs on another thread and reads the call row back. Starting it before the
     * commit is a race it loses: the row is not visible outside this transaction yet, so the
     * analysis would write questions pointing at a call that, from its side, does not exist. And
     * if the transaction then rolled back, we would have analysed a call that never happened.
     */
    private void scheduleAnalysis(UUID tenantId, UUID callId, String transcript) {
        if (transcript == null || transcript.isBlank()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    callAnalysisService.analyzeAsync(tenantId, callId, transcript);
                }
            });
        } else {
            callAnalysisService.analyzeAsync(tenantId, callId, transcript);
        }
    }

    /**
     * Same precedence as {@code VoiceWebhookService.resolveTenantId}: dialed number first (the
     * real routing mechanism), then call metadata, then the seeded CES tenant as a last resort so
     * an unmatched call still gets recorded instead of dropped.
     */
    private UUID resolveTenantId(JsonNode message) {
        UUID byDialedNumber = resolveTenantIdByDialedNumber(message);
        if (byDialedNumber != null) {
            return byDialedNumber;
        }

        JsonNode metadata = message.path("call").path("metadata");
        String raw = metadata.path("tenantId").asText(null);
        if (raw == null || raw.isBlank()) {
            raw = metadata.path("tenant_id").asText(null);
        }
        if (raw == null || raw.isBlank()) {
            log.warn("No tenant match by dialed number or metadata on end-of-call-report - falling back to "
                    + "the bootstrap default CES tenant {}", DEFAULT_TENANT_ID);
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

    /** See {@code VoiceWebhookService.resolveTenantIdByDialedNumber} for why this never throws. */
    private UUID resolveTenantIdByDialedNumber(JsonNode message) {
        String dialed = message.path("call").path("phoneNumber").path("number").asText(null);
        if (dialed == null || dialed.isBlank()) {
            return null;
        }
        dialed = dialed.trim();
        try {
            Optional<Tenant> tenant = tenantRepository.findByPhoneNumber(dialed);
            if (tenant.isEmpty() && !dialed.startsWith("+")) {
                tenant = tenantRepository.findByPhoneNumber("+" + dialed);
            }
            if (tenant.isPresent()) {
                return tenant.get().getId();
            }
            log.debug("Dialed number '{}' matched no tenant - trying metadata instead", dialed);
        } catch (Exception e) {
            log.warn("Tenant lookup by dialed number '{}' failed - trying metadata instead", dialed, e);
        }
        return null;
    }

    /**
     * Vapi's end-of-call-report has no top-level "detected language" field (per-word confidence
     * arrays do, but that's overkill at this stage). Falls back to the assistant's configured
     * transcriber language, which is currently always "az" for this single-tenant bootstrap setup.
     */
    private String resolveLanguage(JsonNode message) {
        String configured = message.path("assistant").path("transcriber").path("language").asText(null);
        return (configured != null && !configured.isBlank()) ? configured : "az";
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
