package com.starsoft.voint.lead;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeadRepository extends JpaRepository<Lead, UUID> {

    /**
     * Free-text search over the fields an operator would actually recognise a lead by.
     * An empty query returns everything, so the admin table needs no separate "list all" path.
     */
    @Query("""
            SELECT l FROM Lead l
            WHERE (:q = ''
                   OR LOWER(l.fullName) LIKE CONCAT('%', :q, '%')
                   OR LOWER(l.company)  LIKE CONCAT('%', :q, '%')
                   OR LOWER(l.email)    LIKE CONCAT('%', :q, '%')
                   OR LOWER(l.phone)    LIKE CONCAT('%', :q, '%'))
              AND (:status IS NULL OR l.status = :status)
            """)
    Page<Lead> search(@Param("q") String q,
                      @Param("status") LeadStatus status,
                      Pageable pageable);

    /** Backs the double-submit guard: the same person pressing the button twice. */
    Optional<Lead> findFirstByEmailIgnoreCaseAndCreatedAtAfterOrderByCreatedAtDesc(
            String email, Instant after);

    long countByStatus(LeadStatus status);
}
