package com.nvh12.reaction.service.impl;

import com.nvh12.reaction.service.*;
import com.nvh12.reaction.service.dto.BruteForceInput;
import com.nvh12.reaction.service.dto.DetectionType;
import com.nvh12.reaction.service.dto.ReactionAction;
import com.nvh12.reaction.service.dto.ReactionInput;
import com.nvh12.reaction.service.dto.alert.Alert;
import com.nvh12.reaction.service.dto.alert.BruteForceAlert;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
public class BruteForceReactionService extends ReactionService {

    private static final String ATTEMPTS_PREFIX = "brute:attempts:";
    private static final Duration ATTEMPT_WINDOW = Duration.ofMinutes(10);
    private static final int ESCALATION_THRESHOLD = 3;

    // Atomically increments the counter and sets TTL only on the first write.
    private static final DefaultRedisScript<Long> INCR_WITH_EXPIRE = new DefaultRedisScript<>(
            "local c = redis.call('INCR', KEYS[1])\n" +
                    "if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end\n" +
                    "return c",
            Long.class
    );

    private final IpBlockService ipBlockService;
    private final RateLimitService rateLimitService;
    private final RedisTemplate<String, String> redisTemplate;

    public BruteForceReactionService(AlertService alertService, ReactionLogService reactionLogService,
                                     IpBlockService ipBlockService, RateLimitService rateLimitService,
                                     RedisTemplate<String, String> redisTemplate) {
        super(DetectionType.BRUTE_FORCE, alertService, reactionLogService);
        this.ipBlockService = ipBlockService;
        this.rateLimitService = rateLimitService;
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected Alert buildAlertDto(ReactionInput input) {
        BruteForceInput bruteForce = (BruteForceInput) input;
        return BruteForceAlert.builder()
                .detectionType(bruteForce.getDetectionType())
                .sourceIp(bruteForce.getSourceIp())
                .severity(bruteForce.getSeverity())
                .detectedAt(bruteForce.getDetectedAt())
                .windowStart(bruteForce.getWindowStart())
                .windowEnd(bruteForce.getWindowEnd())
                .destIp(bruteForce.getDestIp())
                .destPort(bruteForce.getDestPort())
                .build();
    }

    @Override
    protected ReactionAction doHandle(ReactionInput input) {
        String ip = input.getSourceIp();
        String attemptsKey = ATTEMPTS_PREFIX + ip;

        Long attempts = redisTemplate.execute(INCR_WITH_EXPIRE,
                List.of(attemptsKey), String.valueOf(ATTEMPT_WINDOW.toSeconds()));
        if (attempts == null) attempts = 1L;

        if (attempts >= ESCALATION_THRESHOLD) {
            log.info("Brute force from {} reached {} attempts in window — escalating to block", ip, attempts);
            ipBlockService.block(ip, input.getSeverity());
            return ReactionAction.BLOCK;
        } else {
            log.info("Brute force from {} at attempt {} — applying rate limit", ip, attempts);
            rateLimitService.limit(ip, input.getSeverity());
            return ReactionAction.RATE_LIMIT;
        }
    }
}
