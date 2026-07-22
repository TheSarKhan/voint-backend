package com.starsoft.voint.auth;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        PanelUser user = panelUserRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }
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
        return TokenResponse.bearer(
                jwtService.generateAccessToken(user),
                jwtService.generateRefreshToken(user));
    }

    @Transactional(readOnly = true)
    public PanelUser getByEmail(String email) {
        return panelUserRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("User not found"));
    }
}
