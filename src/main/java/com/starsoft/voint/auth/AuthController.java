package com.starsoft.voint.auth;

import com.starsoft.voint.rbac.Permission;
import com.starsoft.voint.rbac.PublicEndpoint;
import com.starsoft.voint.rbac.RequirePermission;
import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.starsoft.voint.auth.dto.LoginRequest;
import com.starsoft.voint.auth.dto.MeResponse;
import com.starsoft.voint.auth.dto.RefreshRequest;
import com.starsoft.voint.auth.dto.TokenResponse;
import com.starsoft.voint.passwordreset.PasswordResetRateLimiter;
import com.starsoft.voint.passwordreset.PasswordResetService;
import com.starsoft.voint.passwordreset.dto.ForgotPasswordRequest;
import com.starsoft.voint.passwordreset.dto.ResetPasswordRequest;
import com.starsoft.voint.rbac.RoleRepository;
import com.starsoft.voint.rbac.PermissionResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "JWT authentication for panel users")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final PasswordResetRateLimiter resetRateLimiter;
    private final RoleRepository roleRepository;
    private final PermissionResolver permissionResolver;

    @PublicEndpoint("Giris")
    @PostMapping("/login")
    @Operation(summary = "Login with email + password, returns access & refresh JWTs")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PublicEndpoint("Token yenileme")
    @PostMapping("/refresh")
    @Operation(summary = "Exchange a refresh token for a new token pair")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }

    @PublicEndpoint("Her giris etmis istifadeci oz melumatini gorur")
    @GetMapping("/me")
    @Operation(summary = "Current authenticated panel user", security = @SecurityRequirement(name = "bearerAuth"))
    public MeResponse me(Principal principal) {
        var user = authService.getByEmail(principal.getName());
        String roleName = user.getRoleId() == null ? null
                : roleRepository.findById(user.getRoleId()).map(r -> r.getName()).orElse(null);
        // The nav and buttons decide what to even offer from this - a granular role that lacks
        // BILLING:READ should never show a Hesablaşma link that 403s the moment it's clicked.
        Map<String, List<String>> permissions = new LinkedHashMap<>();
        if (user.getRoleId() != null) {
            permissionResolver.permissionsOf(user.getRoleId())
                    .forEach((resource, actions) -> permissions.put(resource.name(),
                            actions.stream().map(Enum::name).toList()));
        }
        return MeResponse.from(user, roleName, permissions);
    }

    /**
     * "Şifrəmi unutdum" — e-poçtla sıfırlama linki göndərir. Cavab həmişə 202-dir: e-poçtun
     * qeydiyyatda olub-olmaması AÇIQLANMIR (bax PasswordResetService).
     */
    @PublicEndpoint("Sifremi unutdum - reset linki gonderir")
    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Request a password reset link by email")
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request,
                               HttpServletRequest http) {
        if (!resetRateLimiter.tryAcquire(clientIp(http))) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Çox sayda sorğu göndərilib. Bir qədər sonra yenidən cəhd edin.");
        }
        passwordResetService.requestReset(request.email());
    }

    /** Linkdəki token + yeni şifrə. Uğurlu olsa köhnə şifrə işləməyi dayandırır. */
    @PublicEndpoint("Reset token ile yeni sifre teyin etmek")
    @PostMapping("/reset-password")
    @Operation(summary = "Set a new password using a reset token")
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.reset(request.token(), request.newPassword());
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
