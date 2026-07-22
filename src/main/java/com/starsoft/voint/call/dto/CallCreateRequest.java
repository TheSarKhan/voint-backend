package com.starsoft.voint.call.dto;

import java.time.Instant;

import com.starsoft.voint.call.CallStatus;

/** Manual/test creation of a call journal entry (later populated by Vapi call events). */
public record CallCreateRequest(
        String callerNumber,
        String languageDetected,
        CallStatus status,
        Integer durationSeconds,
        Instant startedAt,
        Instant endedAt
) {
}
