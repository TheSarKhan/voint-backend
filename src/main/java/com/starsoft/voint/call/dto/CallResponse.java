package com.starsoft.voint.call.dto;

import java.time.Instant;
import java.util.UUID;

import com.starsoft.voint.call.Call;
import com.starsoft.voint.call.CallStatus;

public record CallResponse(
        UUID id,
        UUID tenantId,
        String callerNumber,
        String languageDetected,
        CallStatus status,
        Integer durationSeconds,
        Instant startedAt,
        Instant endedAt,
        /**
         * Bu zəngdə hələ bağlanmamış neçə cavabsız sual var.
         */
        long openQuestionCount,
        UUID customerId,
        String customerName
) {
    public static CallResponse from(Call c) {
        return from(c, 0L, null, null);
    }

    public static CallResponse from(Call c, long openQuestionCount) {
        return from(c, openQuestionCount, null, null);
    }

    public static CallResponse from(Call c, long openQuestionCount, UUID customerId, String customerName) {
        return new CallResponse(c.getId(), c.getTenantId(), c.getCallerNumber(), c.getLanguageDetected(),
                c.getStatus(), c.getDurationSeconds(), c.getStartedAt(), c.getEndedAt(), openQuestionCount,
                customerId, customerName);
    }
}
