package com.starsoft.voint.approval;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.starsoft.voint.auth.AuthenticatedUser;
import com.starsoft.voint.auth.PanelUser;
import com.starsoft.voint.auth.PanelUserRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Lets an approved request back in, once.
 *
 * <p>Approving does not execute anything by itself: it re-sends the original request to this
 * process, carrying a one-shot secret instead of a token. This filter recognises that secret and
 * authenticates the request AS THE PERSON WHO ASKED - not as the approver. The operation is theirs;
 * the approver only unblocked it, and an audit trail that named the approver as the actor would be
 * a lie.
 *
 * <p>Two things keep the secret from being a back door: it is only accepted from this machine, and
 * it is deleted the moment it is spent, so a replayed replay finds nothing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalReplayFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Voint-Replay";
    /** Set on the request so the gate knows this one has already been through it. */
    public static final String REPLAY_ATTRIBUTE = "voint.approval.replay";

    private final ApprovalRequestRepository repository;
    private final PanelUserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String nonce = request.getHeader(HEADER);
        if (nonce == null || nonce.isBlank()) {
            chain.doFilter(request, response);
            return;
        }

        // The header is only ever set by this application calling itself. Anything arriving with
        // it from outside is an attempt to skip the queue.
        if (!isLoopback(request.getRemoteAddr())) {
            log.warn("Replay header from {} - refused", request.getRemoteAddr());
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        ApprovalRequest approval = repository.findByReplayNonce(nonce).orElse(null);
        if (approval == null || !"APPROVED".equals(approval.getStatus())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        PanelUser actor = approval.getRequestedBy() == null ? null
                : userRepository.findById(approval.getRequestedBy()).orElse(null);
        if (actor == null) {
            // The account was deleted between asking and being approved. Refusing here is right:
            // running it anyway would attribute a change to somebody who no longer exists.
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Sorğunu verən hesab yoxdur");
            return;
        }

        AuthenticatedUser principal = new AuthenticatedUser(
                actor.getEmail(), actor.getTenantId(), actor.getRole(), actor.getRoleId());
        var authentication = new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + actor.getRole())));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        request.setAttribute(REPLAY_ATTRIBUTE, approval.getId());

        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private boolean isLoopback(String address) {
        return "127.0.0.1".equals(address) || "0:0:0:0:0:0:0:1".equals(address)
                || "::1".equals(address);
    }
}
