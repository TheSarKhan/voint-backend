package com.starsoft.voint.reservation;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.starsoft.voint.common.exception.NotFoundException;
import com.starsoft.voint.reservation.dto.ReservationUpdateRequest;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;

    @Transactional(readOnly = true)
    public List<ReservationRequest> list(UUID tenantId) {
        return reservationRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    @Transactional
    public ReservationRequest updateStatus(UUID tenantId, UUID reservationId, ReservationUpdateRequest request) {
        ReservationRequest reservation = reservationRepository.findByIdAndTenantId(reservationId, tenantId)
                .orElseThrow(() -> NotFoundException.of("ReservationRequest", reservationId));
        reservation.setStatus(request.status());
        return reservationRepository.save(reservation);
    }
}
