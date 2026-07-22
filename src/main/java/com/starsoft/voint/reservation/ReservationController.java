package com.starsoft.voint.reservation;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.starsoft.voint.reservation.dto.ReservationResponse;
import com.starsoft.voint.reservation.dto.ReservationUpdateRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/tenants/{id}/reservations")
@RequiredArgsConstructor
@Tag(name = "Reservations", description = "Reservation requests collected by the agent (pending human confirmation)")
public class ReservationController {

    private final ReservationService reservationService;

    @GetMapping
    @Operation(summary = "List reservation requests of the tenant")
    public List<ReservationResponse> list(@PathVariable("id") UUID tenantId) {
        return reservationService.list(tenantId).stream().map(ReservationResponse::from).toList();
    }

    @PatchMapping("/{resId}")
    @Operation(summary = "Confirm or reject a reservation request")
    public ReservationResponse update(@PathVariable("id") UUID tenantId,
                                      @PathVariable UUID resId,
                                      @Valid @RequestBody ReservationUpdateRequest request) {
        return ReservationResponse.from(reservationService.updateStatus(tenantId, resId, request));
    }
}
