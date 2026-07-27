package com.starsoft.voint.usage;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Writes one {@link UsageEvent} per conversation turn.
 *
 * <p>Metering must never take a live call down. If the write fails the caller still gets its
 * answer and we lose one turn from the bill, which is strictly better than dropping the call.
 *
 * <p>The catch therefore sits <em>outside</em> the transaction, in this class, while the actual
 * insert happens in {@link UsageWriter}. Catching inside a {@code @Transactional} method would not
 * work: a failed insert marks the transaction rollback-only and Hibernate may defer the statement
 * to flush time, so the exception surfaces when the proxy commits - after any inner catch block
 * has already been passed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UsageRecorder {

    private final UsageWriter usageWriter;
    private final UsageEventRepository usageEventRepository;

    /**
     * True when nothing has been recorded for this Vapi call yet, i.e. the caller has just spoken
     * for the first time. Unknown call ids count as "first" so a limit is still applied when Vapi
     * sends no id at all.
     */
    public boolean isFirstTurn(String vapiCallId) {
        if (vapiCallId == null || vapiCallId.isBlank()) {
            return true;
        }
        try {
            return !usageEventRepository.existsByVapiCallId(vapiCallId);
        } catch (Exception e) {
            // A failed lookup must not decide policy; assume mid-call so nobody gets cut off.
            log.error("Could not determine whether call {} is new - treating it as in-progress",
                    vapiCallId, e);
            return false;
        }
    }

    /**
     * @param tenantExists whether the resolved tenant is actually a row in {@code tenants}. The
     *                     voice pipeline tolerates an unknown tenant id and answers anyway, but
     *                     inserting a usage row for one would violate the foreign key - and that
     *                     failure would arrive mid-call.
     */
    public void record(UUID tenantId, boolean tenantExists, String vapiCallId, int promptTokens,
                       int completionTokens, int ttsCharacters) {
        if (tenantId == null) {
            return;
        }
        if (!tenantExists) {
            log.warn("Skipping usage metering for unknown tenant {} - {} TTS chars and {} tokens on "
                            + "vapi call {} will not be billed to anyone.",
                    tenantId, ttsCharacters, promptTokens + completionTokens, vapiCallId);
            return;
        }
        try {
            usageWriter.write(tenantId, vapiCallId, promptTokens, completionTokens, ttsCharacters);
        } catch (Exception e) {
            log.error("Could not record usage for tenant {} (vapi call {}): {} prompt + {} completion "
                            + "tokens, {} TTS chars. The call is unaffected, but this turn will be "
                            + "missing from the tenant's bill.",
                    tenantId, vapiCallId, promptTokens, completionTokens, ttsCharacters, e);
        }
    }

    /**
     * Vapi's call id, when present. The custom-LLM webhook keeps {@code call} as a loose map at
     * this stage, so read it defensively rather than binding a DTO to a shape Vapi may change.
     */
    public static String extractVapiCallId(Map<String, Object> call) {
        if (call == null) {
            return null;
        }
        Object id = call.get("id");
        return id != null ? id.toString() : null;
    }
}
