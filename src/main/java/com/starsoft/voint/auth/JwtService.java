package com.starsoft.voint.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;

/**
 * Minimal JWT skeleton (HS256 via jjwt). Access + refresh tokens share the same
 * secret; refresh tokens carry a "type":"refresh" claim.
 */
@Service
public class JwtService {

    @Value("${voint.jwt.secret}")
    private String secret;

    @Value("${voint.jwt.access-ttl-minutes:60}")
    private long accessTtlMinutes;

    @Value("${voint.jwt.refresh-ttl-days:7}")
    private long refreshTtlDays;

    private SecretKey key;

    @PostConstruct
    void init() {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(PanelUser user) {
        return buildToken(user, Duration.ofMinutes(accessTtlMinutes), "access");
    }

    public String generateRefreshToken(PanelUser user) {
        return buildToken(user, Duration.ofDays(refreshTtlDays), "refresh");
    }

    private String buildToken(PanelUser user, Duration ttl, String type) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(user.getEmail())
                .claim("uid", user.getId().toString())
                .claim("role", user.getRole())
                .claim("type", type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)));
        // tenantId is nullable: a platform-wide SUPER_ADMIN isn't scoped to a single tenant.
        if (user.getTenantId() != null) {
            builder.claim("tenantId", user.getTenantId().toString());
        }
        return builder.signWith(key).compact();
    }

    /** Parses and validates signature + expiry; throws JwtException on failure. */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isRefreshToken(Claims claims) {
        return "refresh".equals(claims.get("type", String.class));
    }
}
