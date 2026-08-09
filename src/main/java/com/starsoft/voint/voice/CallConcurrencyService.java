package com.starsoft.voint.voice;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

/**
 * Per-business call-line reservation, held in Redis rather than JVM memory so a restart or a
 * second backend replica cannot accidentally give the same line to two calls.
 */
@Service @RequiredArgsConstructor
public class CallConcurrencyService {
    private static final Duration SAFETY_TTL = Duration.ofHours(8);
    private static final DefaultRedisScript<Long> RESERVE = new DefaultRedisScript<>(
            "local added=redis.call('SADD',KEYS[1],ARGV[1]); "
                    + "local count=redis.call('SCARD',KEYS[1]); "
                    + "if added==1 and count>tonumber(ARGV[2]) then redis.call('SREM',KEYS[1],ARGV[1]); return 0; end; "
                    + "redis.call('EXPIRE',KEYS[1],ARGV[3]); return 1;", Long.class);

    private final StringRedisTemplate redis;

    /** True means this call owns (or already owned) one of the tenant's permitted call lines. */
    public boolean reserve(UUID tenantId, String vapiCallId, int limit) {
        if (vapiCallId == null || vapiCallId.isBlank()) return true; // Vapi normally sends it; don't kill a valid call on malformed metadata.
        Long result = redis.execute(RESERVE, List.of(key(tenantId)), vapiCallId, String.valueOf(limit), String.valueOf(SAFETY_TTL.toSeconds()));
        return result != null && result == 1L;
    }

    public void release(UUID tenantId, String vapiCallId) {
        if (vapiCallId != null && !vapiCallId.isBlank()) redis.opsForSet().remove(key(tenantId), vapiCallId);
    }

    private String key(UUID tenantId) { return "voint:active-calls:" + tenantId; }
}
