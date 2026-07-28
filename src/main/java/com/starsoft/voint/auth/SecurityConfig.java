package com.starsoft.voint.auth;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.starsoft.voint.voice.VapiWebhookAuthFilter;

import lombok.RequiredArgsConstructor;

/**
 * Bootstrap security: stateless JWT, permitAll on the Vapi webhook, auth,
 * Swagger and actuator; everything else requires a bearer token.
 * The Vapi webhook is permitAll at the Spring Security authorization level (Vapi's cloud
 * infrastructure can't present a panel JWT) but is still protected by {@link VapiWebhookAuthFilter},
 * which enforces the {@code VAPI_WEBHOOK_SECRET} shared-secret header once one is configured.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final VapiWebhookAuthFilter vapiWebhookAuthFilter;
    private final com.starsoft.voint.approval.ApprovalReplayFilter approvalReplayFilter;

    @Value("${voint.cors.allowed-origins:http://localhost:5173}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/voice/**",
                                "/api/v1/auth/login",
                                // Panel bunu login-den EVVEL cagirir: hansi muessiseye aid
                                // oldugunu bilmese, giris ekraninda adini yaza bilmez.
                                "/api/v1/public/**",
                                "/api/v1/auth/refresh",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/actuator/**",
                                "/error"
                        ).permitAll()
                        .anyRequest().authenticated())
                // Without this, Spring Security falls back to Http403ForbiddenEntryPoint (no
                // formLogin/httpBasic is configured), so a missing or expired token answers 403 -
                // indistinguishable from "authenticated but not allowed". The panels then never
                // recognise an expired session and sit on a dead page issuing failing requests.
                // 401 = who are you; 403 (from TenantAccessGuard) = I know you, you may not.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(vapiWebhookAuthFilter, UsernamePasswordAuthenticationFilter.class)
                // Before the JWT filter: an approved operation is replayed with a one-shot secret
                // instead of a token, and that secret authenticates it as the person who asked.
                .addFilterBefore(approvalReplayFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Keeps the replay filter out of the plain servlet chain.
     *
     * <p>Spring Boot registers every Filter bean with the servlet container as well, where it would
     * run before Spring Security's own context filter - which then replaces whatever authentication
     * it had installed with an empty one, and OncePerRequestFilter would not let it run a second
     * time inside the chain to put it back. Disabling that registration leaves exactly one place it
     * runs: after the context filter, where what it sets survives.
     */
    @Bean
    public FilterRegistrationBean<com.starsoft.voint.approval.ApprovalReplayFilter>
            approvalReplayFilterRegistration(
                    com.starsoft.voint.approval.ApprovalReplayFilter filter) {
        FilterRegistrationBean<com.starsoft.voint.approval.ApprovalReplayFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    /** Allows the panel's browser origin(s) to call the API directly (see voint.cors.allowed-origins). */
    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
