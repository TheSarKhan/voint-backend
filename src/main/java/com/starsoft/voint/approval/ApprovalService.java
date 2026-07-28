package com.starsoft.voint.approval;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.starsoft.voint.approval.dto.ApprovalResponse;
import com.starsoft.voint.auth.AuthenticatedUser;
import com.starsoft.voint.auth.PanelUser;
import com.starsoft.voint.auth.PanelUserRepository;
import com.starsoft.voint.rbac.Permission;
import com.starsoft.voint.rbac.PermissionResolver;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The approval queue: what was asked, who may decide, and what happens when they do.
 *
 * @see ApprovalWriter for why the state changes live in a separate bean
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalService {

    private final ApprovalRequestRepository repository;
    private final ApprovalWriter writer;
    private final PanelUserRepository userRepository;
    private final PermissionResolver permissions;

    /** The replay goes back through the front door of this same process. */
    @Value("${server.port:8080}")
    private int serverPort;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /**
     * Records the request instead of running it.
     *
     * <p>REQUIRES_NEW: the caller is an interceptor that is about to abandon this request. The
     * record of what was asked must survive that regardless.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ApprovalRequest hold(AuthenticatedUser user, HttpServletRequest request,
                                Permission.Resource resource, Permission.Action action) {
        PanelUser actor = userRepository.findByEmailIgnoreCase(user.email()).orElse(null);

        return repository.save(ApprovalRequest.builder()
                .tenantId(user.tenantId())
                .requestedBy(actor != null ? actor.getId() : null)
                .requestedByEmail(user.email())
                .method(request.getMethod())
                .path(request.getRequestURI())
                .queryString(request.getQueryString())
                .body(readBody(request))
                .resource(resource.name())
                .action(action.name())
                .summary(ApprovalGateInterceptor.describe(resource, action))
                .status("PENDING")
                .build());
    }

    @Transactional(readOnly = true)
    public List<ApprovalResponse> list(UUID tenantId, String status) {
        List<ApprovalRequest> rows = status == null || status.isBlank()
                ? repository.findByTenantIdOrderByCreatedAtDesc(tenantId)
                : repository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, status);
        return rows.stream().map(ApprovalResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public long pendingCount(UUID tenantId) {
        return repository.countByTenantIdAndStatus(tenantId, "PENDING");
    }

    public ApprovalResponse reject(UUID tenantId, UUID id, AuthenticatedUser decider, String note) {
        requireCanDecide(tenantId, writer.requireOfTenant(tenantId, id), decider);
        ApprovalRequest row = writer.reject(tenantId, id, decider, note);
        log.info("Approval {} rejected by {}", id, decider.email());
        return ApprovalResponse.from(row);
    }

    /**
     * Approves, then actually performs the operation.
     *
     * <p>The row is committed as APPROVED before the replay runs, because the replay is a separate
     * request into this same process and has to be able to see it. If the replay then fails the row
     * becomes FAILED rather than going back to PENDING: a person did authorise this, and hiding
     * that would misrepresent what happened.
     */
    public ApprovalResponse approve(UUID tenantId, UUID id, AuthenticatedUser decider, String note) {
        requireCanDecide(tenantId, writer.requireOfTenant(tenantId, id), decider);
        String nonce = writer.markApproved(tenantId, id, decider, note);
        ApprovalRequest row = repository.findById(id).orElseThrow();

        try {
            int status = replay(row, nonce);
            if (status >= 400) {
                return ApprovalResponse.from(writer.fail(id,
                        "Əməliyyat icra olunmadı, server " + status + " qaytardı"));
            }
            log.info("Approval {} executed: {} {} -> {}", id, row.getMethod(), row.getPath(), status);
        } catch (Exception e) {
            log.error("Approval {} could not be executed", id, e);
            return ApprovalResponse.from(writer.fail(id,
                    "Əməliyyat icra olunmadı: " + e.getMessage()));
        } finally {
            writer.clearNonce(id);
        }
        return ApprovalResponse.from(repository.findById(id).orElseThrow());
    }

    /**
     * Whether this person may decide on this request.
     *
     * <p>Deciding on your own request is refused while somebody else could do it instead. When you
     * are the only person in the business who can approve anything - which is the shape of every
     * business on this platform today - it is allowed, because the alternative is a company that
     * cannot delete a wrong phone number until it hires a second manager.
     */
    private void requireCanDecide(UUID tenantId, ApprovalRequest row, AuthenticatedUser decider) {
        if (!decider.email().equalsIgnoreCase(row.getRequestedByEmail())) {
            return;
        }
        if (otherApprovers(tenantId, decider.email()) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Öz sorğunu təsdiqləyə bilməzsən — müəssisədə təsdiq verə bilən başqa şəxs var.");
        }
    }

    /** Active accounts of this tenant, other than the given one, holding APPROVAL · Dəyiş. */
    private long otherApprovers(UUID tenantId, String exceptEmail) {
        return userRepository.findByTenantIdOrderByCreatedAt(tenantId).stream()
                .filter(u -> !u.getEmail().equalsIgnoreCase(exceptEmail))
                .filter(u -> "ACTIVE".equals(u.getStatus()))
                .filter(u -> u.getRoleId() != null)
                .filter(u -> permissions.isAllowed(u.getRoleId(),
                        Permission.Resource.APPROVAL, Permission.Action.UPDATE))
                .count();
    }

    private int replay(ApprovalRequest row, String nonce) throws IOException, InterruptedException {
        String url = "http://127.0.0.1:" + serverPort + row.getPath()
                + (row.getQueryString() == null ? "" : "?" + row.getQueryString());

        HttpRequest.BodyPublisher payload = row.getBody() == null || row.getBody().isBlank()
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(row.getBody(), StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .method(row.getMethod(), payload)
                .header("Content-Type", "application/json")
                .header(ApprovalReplayFilter.HEADER, nonce)
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            log.warn("Replay of approval {} answered {}: {}", row.getId(), response.statusCode(),
                    response.body());
        }
        return response.statusCode();
    }

    private String readBody(HttpServletRequest request) {
        try {
            String body = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return body.isBlank() ? null : body;
        } catch (IOException e) {
            // A DELETE usually has none. Anything else means we could not read what was asked, and
            // a request we cannot reproduce must not sit in the queue pretending otherwise.
            log.warn("Could not read the body of a held request", e);
            return null;
        }
    }
}
