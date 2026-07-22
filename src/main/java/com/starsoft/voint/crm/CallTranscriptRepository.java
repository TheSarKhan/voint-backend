package com.starsoft.voint.crm;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CallTranscriptRepository extends JpaRepository<CallTranscript, UUID> {

    Optional<CallTranscript> findByCallId(UUID callId);
}
