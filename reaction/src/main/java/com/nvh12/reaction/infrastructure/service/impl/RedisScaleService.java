package com.nvh12.reaction.infrastructure.service.impl;

import com.nvh12.reaction.service.ScaleService;
import com.nvh12.reaction.service.dto.DetectionType;
import com.nvh12.reaction.service.dto.Severity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisScaleService implements ScaleService {

    static final String SCALE_STATE = "scale:state";
    static final String SCALE_REPLICAS = "scale:replicas";

    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public void requestScale(DetectionType detectionType, Severity severity) {
        Retry.run(3, 200, DataAccessException.class, () -> {
            int replicas = targetReplicas(severity);
            Duration ttl = ttl(severity);
            redisTemplate.opsForValue().set(SCALE_STATE, "scaled_up", ttl);
            redisTemplate.opsForValue().set(SCALE_REPLICAS, String.valueOf(replicas), ttl);
            log.info("Scale state → scaled_up, replicas={}, ttl={} ({} / {})", replicas, ttl, detectionType, severity);
        }, e -> log.error("Scale request failed after retries [type={} severity={}]: {}", detectionType, severity, e.getMessage()));
    }

    private int targetReplicas(Severity severity) {
        String configKey = "config:scale:replicas:" + severity.name();
        String configured = redisTemplate.opsForValue().get(configKey);
        if (configured != null) {
            try {
                return Integer.parseInt(configured);
            } catch (NumberFormatException ignored) {
            }
        }
        return switch (severity) {
            case NONE -> throw new IllegalStateException("NONE severity should not reach scale service");
            case LOW -> 2;
            case MEDIUM -> 3;
            case HIGH -> 5;
            case CRITICAL -> 8;
        };
    }

    private Duration ttl(Severity severity) {
        return switch (severity) {
            case NONE -> throw new IllegalStateException("NONE severity should not reach scale service");
            case LOW -> Duration.ofMinutes(5);
            case MEDIUM -> Duration.ofMinutes(30);
            case HIGH -> Duration.ofHours(2);
            case CRITICAL -> Duration.ofHours(24);
        };
    }
}
