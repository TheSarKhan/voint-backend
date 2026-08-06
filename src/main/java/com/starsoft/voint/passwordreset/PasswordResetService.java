package com.starsoft.voint.passwordreset;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.starsoft.voint.auth.PanelUser;
import com.starsoft.voint.auth.PanelUserRepository;
import com.starsoft.voint.mail.MailService;
import com.starsoft.voint.mail.MailTemplates;
import com.starsoft.voint.settings.PanelUrls;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * "Şifrəmi unutdum" axını.
 *
 * <p>İki addım: {@link #requestReset} bir token yaradıb e-poçtla link göndərir; {@link #reset}
 * həmin token-lə yeni şifrəni təyin edir. Şifrə YALNIZ ikinci addımda dəyişir — yəni linki açan
 * (deməli e-poçta çıxışı olan) şəxs. Bu, kiminsə başqasının şifrəsini sadəcə e-poçtunu bilməklə
 * sıfırlamasının qarşısını alır.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final Duration TOKEN_TTL = Duration.ofHours(1);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PanelUserRepository panelUserRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mail;
    private final PanelUrls panelUrls;

    /**
     * Token yaradıb sıfırlama linkini e-poçtla göndərir.
     *
     * <p>E-poçtun mövcud olub-olmadığını AÇIQLAMIR: hesab yoxdursa da sükutla uğurlu qayıdır.
     * Əks halda bu endpoint hansı e-poçtların qeydiyyatda olduğunu yoxlamaq üçün istifadə oluna
     * bilərdi. Nəticə çağırana həmişə eynidir.
     */
    @Transactional
    public void requestReset(String rawEmail) {
        String email = rawEmail == null ? "" : rawEmail.trim().toLowerCase();
        PanelUser user = panelUserRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null) {
            log.info("Password reset requested for unknown email (ignored, no signal returned)");
            return;
        }
        if (!mail.isConfigured()) {
            // SMTP qurulmayıbsa link göndərmək mümkün deyil. Bunu çağırana bildiririk — sükutla
            // uğurlu qayıtmaq istifadəçini heç vaxt gəlməyəcək e-poçtu gözlətmək olardı.
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "E-poçt xidməti qurulmayıb - şifrə sıfırlama linki göndərilə bilmir");
        }

        // Köhnə açıq token-ləri ləğv et: eyni anda yalnız bir link işləməlidir.
        tokenRepository.invalidateOpenTokens(user.getId());

        String rawToken = generateToken();
        tokenRepository.save(PasswordResetToken.builder()
                .userId(user.getId())
                .tokenHash(sha256(rawToken))
                .expiresAt(Instant.now().plus(TOKEN_TTL))
                .build());

        String resetUrl = panelUrls.forTenant(user.getTenantId()) + "/reset-password?token=" + rawToken;
        mail.send(user.getEmail(), "Voint şifrə sıfırlama", MailTemplates.passwordResetLink(resetUrl));
        log.info("Password reset link sent to {}", user.getEmail());
    }

    /** Token-i yoxlayıb yeni şifrəni təyin edir. Token birdəfəlikdir və bir saat yaşayır. */
    @Transactional
    public void reset(String rawToken, String newPassword) {
        PasswordResetToken token = tokenRepository.findByTokenHash(sha256(rawToken))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Link etibarsızdır. Yenidən şifrə sıfırlama tələb edin."));

        if (token.getUsedAt() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Bu link artıq istifadə olunub. Yenidən şifrə sıfırlama tələb edin.");
        }
        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Linkin vaxtı bitib. Yenidən şifrə sıfırlama tələb edin.");
        }

        PanelUser user = panelUserRepository.findById(token.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Hesab tapılmadı."));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        panelUserRepository.save(user);

        // Token dərhal bağlanır — eyni link ikinci dəfə işləməməlidir.
        token.setUsedAt(Instant.now());
        tokenRepository.save(token);

        log.info("Password reset completed for {}", user.getEmail());
    }

    /** 32 baytlıq URL-təhlükəsiz təsadüfi token — brute-force üçün əlçatmaz. */
    private String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Token bazada xam yox, hash-lə saxlanılır (bax V15). */
    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
