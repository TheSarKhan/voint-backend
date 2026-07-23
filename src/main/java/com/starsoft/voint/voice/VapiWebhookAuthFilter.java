package com.starsoft.voint.voice;

import java.io.IOException;
import java.net.URI;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Path-specific filter (mirrors {@link com.starsoft.voint.auth.JwtAuthFilter}'s idiom) that
 * enforces {@link VapiWebhookVerifier} on the Vapi custom-LLM webhook only. Every other path is
 * untouched. Runs inside the Spring Security filter chain (wired in {@code SecurityConfig}), so it
 * can reject before the request ever reaches {@link VoiceWebhookController} - but since the
 * response never goes through the MVC dispatcher, we hand-build the same RFC 7807 ProblemDetail
 * shape {@code GlobalExceptionHandler} uses for every other 4xx response.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VapiWebhookAuthFilter extends OncePerRequestFilter {

    // /webhook and /chat/completions hit VoiceWebhookController.webhook() - /chat/completions exists
    // because Vapi's custom-LLM integration appends that suffix to the configured base URL. /events
    // hits VoiceWebhookController.events() - the assistant's separate "server" (call lifecycle) webhook.
    private static final Set<String> WEBHOOK_PATHS = Set.of(
            "/api/v1/voice/webhook",
            "/api/v1/voice/chat/completions",
            "/api/v1/voice/events");

    private final VapiWebhookVerifier verifier;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        boolean isWebhookRequest = WEBHOOK_PATHS.contains(request.getRequestURI());
        if (isWebhookRequest && verifier.isEnabled() && !verifier.verify(request)) {
            log.warn("Rejected Vapi webhook request from {} - missing/invalid {} header",
                    request.getRemoteAddr(), SharedSecretVapiWebhookVerifier.SECRET_HEADER);
            writeUnauthorized(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
                "Missing or invalid " + SharedSecretVapiWebhookVerifier.SECRET_HEADER + " header");
        pd.setTitle("Webhook authentication failed");
        pd.setType(URI.create("https://voint.starsoft.com/errors/unauthorized"));

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), pd);
    }
}
