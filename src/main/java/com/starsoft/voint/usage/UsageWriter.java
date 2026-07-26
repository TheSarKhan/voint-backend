package com.starsoft.voint.usage;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * The transactional half of {@link UsageRecorder}, kept as a separate bean so the commit happens
 * inside a call that {@code UsageRecorder} can wrap in a try/catch. See that class for why.
 *
 * <p>REQUIRES_NEW: metering is bookkeeping alongside the call, not part of it. It must not join -
 * and therefore must not be able to poison - whatever transaction the caller is in.
 */
@Component
@RequiredArgsConstructor
class UsageWriter {

    private final UsageEventRepository usageEventRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(UUID tenantId, String vapiCallId, int promptTokens, int completionTokens,
                      int ttsCharacters) {
        usageEventRepository.save(UsageEvent.builder()
                .tenantId(tenantId)
                .vapiCallId(vapiCallId)
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .ttsCharacters(ttsCharacters)
                .build());
    }
}
