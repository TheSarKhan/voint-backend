package com.starsoft.voint.auth;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.starsoft.voint.auth.dto.LoginRequest;
import com.starsoft.voint.auth.dto.RefreshRequest;
import com.starsoft.voint.auth.dto.TokenResponse;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final PanelUserRepository panelUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /**
     * Not read-only: a real credentialed login is also the moment {@code lastLoginAt} is stamped -
     * see the Komanda/Team screen's "Son giriş" column. Deliberately NOT done in {@link #refresh},
     * which fires silently every few minutes while a tab is open - stamping there would turn "last
     * login" into "browser was open", which is a different (and less useful) fact.
     */
    @Transactional
    public TokenResponse login(LoginRequest request) {
        PanelUser user = panelUserRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }
        requireActive(user);
        user.setLastLoginAt(Instant.now());
        panelUserRepository.save(user);
        return TokenResponse.bearer(
                jwtService.generateAccessToken(user),
                jwtService.generateRefreshToken(user));
    }

    @Transactional(readOnly = true)
    public TokenResponse refresh(RefreshRequest request) {
        Claims claims;
        try {
            claims = jwtService.parse(request.refreshToken());
        } catch (JwtException | IllegalArgumentException e) {
            throw new BadCredentialsException("Invalid refresh token");
        }
        if (!jwtService.isRefreshToken(claims)) {
            throw new BadCredentialsException("Not a refresh token");
        }
        PanelUser user = panelUserRepository.findByEmail(claims.getSubject())
                .orElseThrow(() -> new BadCredentialsException("User no longer exists"));
        requireActive(user);
        return TokenResponse.bearer(
                jwtService.generateAccessToken(user),
                jwtService.generateRefreshToken(user));
    }

    /**
     * Blocking has to bite on the next login/refresh too, not just on API calls the
     * {@code PermissionInterceptor} happens to guard - otherwise a blocked account keeps minting
     * fresh tokens for as long as its refresh token lives.
     */
    private void requireActive(PanelUser user) {
        if ("BLOCKED".equals(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Hesabınız bloklanıb");
        }
    }

    @Transactional(readOnly = true)
    public PanelUser getByEmail(String email) {
        return panelUserRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("User not found"));
    }
}
