package com.nvh12.reaction.infrastructure.service.impl;

import com.nvh12.reaction.service.RateLimitService;
import com.nvh12.reaction.service.dto.Severity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisRateLimitService implements RateLimitService {

    static final String WHITELIST_IPS = "whitelist:ips";
    static final String COUNTER_PREFIX = "ratelimit:ip:";
    static final String LIMIT_SUFFIX = ":limit";
    static final String WINDOW_END_SUFFIX = ":window_end";

    private static final Duration WINDOW = Duration.ofSeconds(60);

    // Atomically writes the limit-policy key, the request counter, and the window-end
    // timestamp in one round-trip. The window-end is written using Redis server time
    // (TIME command) to avoid clock skew between the JVM and Redis. The counter gets
    // a 60-second TTL so it resets each window; window_end and the limit key live for
    // the full policy TTL so the middleware can anchor new windows to the same grid.
    private static final DefaultRedisScript<Long> SET_RATE_LIMIT = new DefaultRedisScript<>(
            "redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[2])\n" +
                    "local win_end = redis.call('TIME')[1] + tonumber(ARGV[3])\n" +
                    "redis.call('SET', KEYS[2], '0', 'EX', ARGV[3])\n" +
                    "redis.call('SET', KEYS[3], tostring(win_end), 'EX', ARGV[2])\n" +
                    "return 1",
            Long.class
    );

    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public void limit(String ip, Severity severity) {
        Retry.run(3, 200, DataAccessException.class, () -> {
            if (Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(WHITELIST_IPS, ip))) {
                log.info("Skipping rate limit for whitelisted IP {}", ip);
                return;
            }
            int rpm = requestsPerMinute(severity);
            Duration policyTtl = ttl(severity);
            redisTemplate.execute(SET_RATE_LIMIT,
                    List.of(
                            COUNTER_PREFIX + ip + LIMIT_SUFFIX,
                            COUNTER_PREFIX + ip,
                            COUNTER_PREFIX + ip + WINDOW_END_SUFFIX),
                    String.valueOf(rpm),
                    String.valueOf(policyTtl.toSeconds()),
                    String.valueOf(WINDOW.toSeconds()));
            log.info("Rate-limited IP {} to {} req/min for {} ({})", ip, rpm, policyTtl, severity);
        }, e -> log.error("Rate limit failed after retries [ip={} severity={}]: {}", ip, severity, e.getMessage()));
    }

    @Override
    public boolean isLimited(String ip) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(COUNTER_PREFIX + ip + LIMIT_SUFFIX));
        } catch (DataAccessException e) {
            log.error("Redis unavailable checking rate limit for {} — failing open: {}", ip, e.getMessage());
            return false;
        }
    }

    private int requestsPerMinute(Severity severity) {
        return switch (severity) {
            case NONE -> throw new IllegalStateException("NONE severity should not reach rate limiting");
            case LOW -> 30;
            case MEDIUM -> 10;
            case HIGH -> 3;
            case CRITICAL -> 1;
        };
    }

    private Duration ttl(Severity severity) {
        return switch (severity) {
            case NONE -> throw new IllegalStateException("NONE severity should not reach rate limiting");
            case LOW -> Duration.ofMinutes(5);
            case MEDIUM -> Duration.ofMinutes(30);
            case HIGH -> Duration.ofHours(2);
            case CRITICAL -> Duration.ofHours(24);
        };
    }
}
