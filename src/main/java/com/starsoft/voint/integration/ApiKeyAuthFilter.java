package com.starsoft.voint.integration;

import java.io.IOException;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-Voint-Api-Key";
    public static final String TENANT_ID_ATTR = "VOINT_EXTERNAL_TENANT_ID";
    public static final String API_KEY_ATTR = "VOINT_EXTERNAL_API_KEY";

    private final ApiKeyService apiKeyService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        if (path.startsWith("/api/v1/external/")) {
            String apiKey = request.getHeader(API_KEY_HEADER);
            if (apiKey == null || apiKey.isBlank()) {
                String authHeader = request.getHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer vk_live_")) {
                    apiKey = authHeader.substring(7);
                }
            }

            if (apiKey == null || apiKey.isBlank()) {
                log.warn("External API request to {} rejected: Missing X-Voint-Api-Key header", path);
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.setContentType("application/json");
                response.getWriter().write("{\"error\": \"Unauthorized\", \"message\": \"Missing X-Voint-Api-Key header\"}");
                return;
            }

            Optional<TenantApiKey> matchedKey = apiKeyService.validateKey(apiKey);
            if (matchedKey.isEmpty()) {
                log.warn("External API request to {} rejected: Invalid API Key", path);
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.setContentType("application/json");
                response.getWriter().write("{\"error\": \"Unauthorized\", \"message\": \"Invalid or inactive API Key\"}");
                return;
            }

            TenantApiKey key = matchedKey.get();
            request.setAttribute(TENANT_ID_ATTR, key.getTenantId());
            request.setAttribute(API_KEY_ATTR, key);
        }

        filterChain.doFilter(request, response);
    }
}
