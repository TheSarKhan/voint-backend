package com.starsoft.voint.lead;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Caps how often one IP may post the public pilot form.
 *
 * <p>The endpoint is unauthenticated and writes a row, which is exactly the shape of thing that
 * gets found and hammered. A counter per IP with a one-hour window is enough: a real business
 * fills this form once, not twenty times.
 *
 * <p><b>Fails open on purpose.</b> If Redis is unreachable the form still works. Losing the pilot
 * requests of every visitor for the duration of a Redis outage is a worse outcome than accepting
 * unthrottled submissions for that window - and the admin table shows the flood either way.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LeadRateLimiter {

    private static final int MAX_PER_WINDOW = 5;
    private static final Duration WINDOW = Duration.ofHours(1);

    private final StringRedisTemplate redis;

    /** @return true when this IP is still within its allowance. */
    public boolean tryAcquire(String ip) {
        if (ip == null || ip.isBlank()) {
            return true;
        }
        String key = "voint:lead-rate:" + ip;
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                // Only the first hit sets the TTL, otherwise the window would slide forever
                // and a persistent poster would never be released.
                redis.expire(key, WINDOW);
            }
            return count == null || count <= MAX_PER_WINDOW;
        } catch (RuntimeException e) {
            log.warn("Lead rate limit could not be checked, letting the request through: {}",
                    e.getMessage());
            return true;
        }
    }
}
