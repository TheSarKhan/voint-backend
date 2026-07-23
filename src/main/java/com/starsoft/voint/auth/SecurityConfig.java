package com.starsoft.voint.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

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

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/voice/**",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/actuator/**",
                                "/error"
                        ).permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(vapiWebhookAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
