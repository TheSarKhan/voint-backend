package com.starsoft.voint.usage;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Writes one {@link UsageEvent} per conversation turn.
 *
 * <p>Metering must never take a live call down: if the write fails the caller still gets its
 * answer and we lose one turn from the bill, which is strictly better than dropping the call.
 * That is also why this runs in its own transaction - a metering failure cannot poison whatever
 * the voice pipeline is doing around it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UsageRecorder {

    private final UsageEventRepository usageEventRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID tenantId, String vapiCallId, int promptTokens, int completionTokens,
                       int ttsCharacters) {
        if (tenantId == null) {
            return;
        }
        try {
            usageEventRepository.save(UsageEvent.builder()
                    .tenantId(tenantId)
                    .vapiCallId(vapiCallId)
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .ttsCharacters(ttsCharacters)
                    .build());
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
