package com.starsoft.voint.passwordreset;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * "Şifrəmi unutdum" sorğularını IP üzrə məhdudlaşdırır (LeadRateLimiter ilə eyni naxış).
 *
 * <p>Endpoint autentifikasiyasızdır və e-poçt göndərir — yəni həm spam üçün, həm də başqasının
 * poçtunu sıfırlama linkləri ilə doldurmaq üçün açıq hədəfdir. Saatda bir neçə cəhd real
 * istifadəçi üçün kifayətdir.
 *
 * <p><b>Qəsdən "fail open".</b> Redis əlçatmazsa sorğu keçir: şifrəsini unudan istifadəçini
 * Redis nasazlığı boyu kilidləmək, bir müddət limitsiz sorğu qəbul etməkdən pisdir.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordResetRateLimiter {

    private static final int MAX_PER_WINDOW = 5;
    private static final Duration WINDOW = Duration.ofHours(1);

    private final StringRedisTemplate redis;

    /** @return true when this IP is still within its allowance. */
    public boolean tryAcquire(String ip) {
        if (ip == null || ip.isBlank()) {
            return true;
        }
        String key = "voint:pwreset-rate:" + ip;
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, WINDOW);
            }
            return count == null || count <= MAX_PER_WINDOW;
        } catch (RuntimeException e) {
            log.warn("Password-reset rate limit could not be checked, letting the request through: {}",
                    e.getMessage());
            return true;
        }
    }
}
