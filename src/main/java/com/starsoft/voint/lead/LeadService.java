package com.starsoft.voint.lead;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.starsoft.voint.lead.dto.LeadCreateRequest;
import com.starsoft.voint.lead.dto.LeadUpdateRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeadService {

    /** Columns the admin table may sort by. Anything else falls back to {@link #DEFAULT_SORT}. */
    public static final Set<String> SORTABLE =
            Set.of("fullName", "company", "email", "status", "createdAt");

    public static final String DEFAULT_SORT = "createdAt";

    /**
     * A second submission from the same email inside this window is treated as the same request.
     * People double-click, and a slow network makes them press "Sorğu göndər" again - neither
     * should produce two rows for an operator to reconcile by hand.
     */
    private static final Duration DUPLICATE_WINDOW = Duration.ofMinutes(30);

    private final LeadRepository leadRepository;

    @Transactional
    public Lead submit(LeadCreateRequest request) {
        String email = request.email().trim();

        var recent = leadRepository
                .findFirstByEmailIgnoreCaseAndCreatedAtAfterOrderByCreatedAtDesc(
                        email, Instant.now().minus(DUPLICATE_WINDOW));
        if (recent.isPresent()) {
            log.info("Duplicate pilot request from {} within {} - returning the existing one",
                    email, DUPLICATE_WINDOW);
            return recent.get();
        }

        Lead lead = Lead.builder()
                .fullName(request.fullName().trim())
                .company(request.company().trim())
                .industry(blankToNull(request.industry()))
                .phone(request.phone().trim())
                .email(email)
                .dailyCallVolume(blankToNull(request.dailyCallVolume()))
                .source("landing")
                .status(LeadStatus.NEW)
                .build();

        lead = leadRepository.save(lead);
        log.info("New pilot request: {} ({})", lead.getCompany(), lead.getEmail());
        return lead;
    }

    @Transactional(readOnly = true)
    public Page<Lead> search(String query, LeadStatus status, Pageable pageable) {
        String q = query == null ? "" : query.trim().toLowerCase();
        return leadRepository.search(q, status, pageable);
    }

    @Transactional(readOnly = true)
    public long countNew() {
        return leadRepository.countByStatus(LeadStatus.NEW);
    }

    @Transactional
    public Lead update(UUID id, LeadUpdateRequest request) {
        Lead lead = leadRepository.findById(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Belə sorğu yoxdur"));
        lead.setStatus(request.status());
        lead.setNote(blankToNull(request.note()));
        lead.setUpdatedAt(Instant.now());
        return leadRepository.save(lead);
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
