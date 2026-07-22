package com.starsoft.voint.reservation.dto;

import java.time.Instant;
import java.util.UUID;

import com.starsoft.voint.reservation.ReservationRequest;
import com.starsoft.voint.reservation.ReservationStatus;

public record ReservationResponse(
        UUID id,
        UUID tenantId,
        UUID callId,
        String customerName,
        String requestedService,
        String requestedDate,
        ReservationStatus status,
        Instant createdAt
) {
    public static ReservationResponse from(ReservationRequest r) {
        return new ReservationResponse(r.getId(), r.getTenantId(), r.getCallId(), r.getCustomerName(),
                r.getRequestedService(), r.getRequestedDate(), r.getStatus(), r.getCreatedAt());
    }
}
