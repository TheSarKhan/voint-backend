package com.starsoft.voint.reservation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<ReservationRequest, UUID> {

    List<ReservationRequest> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<ReservationRequest> findByIdAndTenantId(UUID id, UUID tenantId);
}
