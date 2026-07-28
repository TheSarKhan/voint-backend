package com.starsoft.voint.approval;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.starsoft.voint.auth.AuthenticatedUser;
import com.starsoft.voint.auth.PanelUserRepository;
import com.starsoft.voint.common.exception.NotFoundException;

import lombok.RequiredArgsConstructor;

/**
 * The writes an approval goes through, each in its own transaction.
 *
 * <p>Separate from {@link ApprovalService} because a single approval is three commits that must not
 * be one: the row has to be visible as APPROVED before the replay - a second HTTP request into this
 * same process - can find it, and the outcome has to be recorded after that request has finished.
 * Calling these from inside the service would also have skipped the proxy and quietly dropped the
 * transaction boundaries altogether.
 */
@Service
@RequiredArgsConstructor
public class ApprovalWriter {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ApprovalRequestRepository repository;
    private final PanelUserRepository userRepository;

    /** @return the one-shot secret the replay must present. */
    @Transactional
    public String markApproved(UUID tenantId, UUID id, AuthenticatedUser decider, String note) {
        ApprovalRequest row = requirePending(tenantId, id);
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        row.setStatus("APPROVED");
        row.setReplayNonce(nonce);
        stampDecision(row, decider, note);
        repository.save(row);
        return nonce;
    }

    @Transactional
    public ApprovalRequest reject(UUID tenantId, UUID id, AuthenticatedUser decider, String note) {
        ApprovalRequest row = requirePending(tenantId, id);
        row.setStatus("REJECTED");
        stampDecision(row, decider, note);
        return repository.save(row);
    }

    @Transactional
    public ApprovalRequest fail(UUID id, String detail) {
        ApprovalRequest row = repository.findById(id).orElseThrow();
        row.setStatus("FAILED");
        row.setFailureDetail(detail);
        return repository.save(row);
    }

    /** Spent or not, the secret must not survive the attempt. */
    @Transactional
    public void clearNonce(UUID id) {
        repository.findById(id).ifPresent(row -> {
            row.setReplayNonce(null);
            repository.save(row);
        });
    }

    @Transactional(readOnly = true)
    public ApprovalRequest requireOfTenant(UUID tenantId, UUID id) {
        ApprovalRequest row = repository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Təsdiq sorğusu", id));
        if (!row.getTenantId().equals(tenantId)) {
            // Not "forbidden": one business must not be able to learn that another's request exists.
            throw NotFoundException.of("Təsdiq sorğusu", id);
        }
        return row;
    }

    private ApprovalRequest requirePending(UUID tenantId, UUID id) {
        ApprovalRequest row = requireOfTenant(tenantId, id);
        if (!row.pending()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Bu sorğu artıq cavablandırılıb.");
        }
        return row;
    }

    private void stampDecision(ApprovalRequest row, AuthenticatedUser decider, String note) {
        row.setDecidedAt(Instant.now());
        row.setDecidedByEmail(decider.email());
        row.setDecisionNote(note != null && !note.isBlank() ? note.trim() : null);
        userRepository.findByEmailIgnoreCase(decider.email())
                .ifPresent(u -> row.setDecidedBy(u.getId()));
    }
}
