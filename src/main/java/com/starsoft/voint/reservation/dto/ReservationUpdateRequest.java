package com.starsoft.voint.reservation.dto;

import com.starsoft.voint.reservation.ReservationStatus;

import jakarta.validation.constraints.NotNull;

/** Panel user confirms or rejects a pending reservation request. */
public record ReservationUpdateRequest(
        @NotNull ReservationStatus status
) {
}
