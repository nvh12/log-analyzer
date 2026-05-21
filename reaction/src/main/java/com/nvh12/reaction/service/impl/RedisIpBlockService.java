package com.nvh12.reaction.service.impl;

import com.nvh12.reaction.service.IpBlockService;
import com.nvh12.reaction.service.dto.Severity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisIpBlockService implements IpBlockService {

    static final String WHITELIST_IPS        = "whitelist:ips";
    static final String BLOCKLIST_IPS        = "blocklist:ips";
    static final String BLOCKLIST_IP_PREFIX  = "blocklist:ip:";

    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public void block(String ip, Severity severity) {
        Retry.run(3, 200, DataAccessException.class, () -> {
            if (Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(WHITELIST_IPS, ip))) {
                log.info("Skipping block for whitelisted IP {}", ip);
                return;
            }
            String metadata = "severity=%s;blocked_at=%s".formatted(severity.name(), Instant.now());
            Duration ttl = ttl(severity);
            redisTemplate.opsForValue().set(BLOCKLIST_IP_PREFIX + ip, metadata, ttl);
            redisTemplate.opsForSet().add(BLOCKLIST_IPS, ip);
            log.info("Blocked IP {} for {} ({})", ip, ttl, severity);
        }, e -> log.error("IP block failed after retries [ip={} severity={}]: {}", ip, severity, e.getMessage()));
    }

    @Override
    public boolean isBlocked(String ip) {
        try {
            boolean active = Boolean.TRUE.equals(redisTemplate.hasKey(BLOCKLIST_IP_PREFIX + ip));
            if (!active) {
                redisTemplate.opsForSet().remove(BLOCKLIST_IPS, ip);
            }
            return active;
        } catch (DataAccessException e) {
            log.error("Redis unavailable checking block status for {} — failing open: {}", ip, e.getMessage());
            return false;
        }
    }

    private Duration ttl(Severity severity) {
        return switch (severity) {
            case LOW      -> Duration.ofMinutes(5);
            case MEDIUM   -> Duration.ofMinutes(30);
            case HIGH     -> Duration.ofHours(2);
            case CRITICAL -> Duration.ofHours(24);
        };
    }
}
