package com.nvh12.reaction.service.impl;

import com.nvh12.reaction.service.AlertService;
import com.nvh12.reaction.service.IpBlockService;
import com.nvh12.reaction.service.RateLimitService;
import com.nvh12.reaction.service.ReactionLogService;
import com.nvh12.reaction.service.ReactionService;
import com.nvh12.reaction.service.dto.DDoSInput;
import com.nvh12.reaction.service.dto.DetectionType;
import com.nvh12.reaction.service.dto.ReactionAction;
import com.nvh12.reaction.service.dto.ReactionInput;
import com.nvh12.reaction.service.dto.alert.Alert;
import com.nvh12.reaction.service.dto.alert.DDoSAlert;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
public class DDoSReactionService extends ReactionService {

    private static final String ATTEMPTS_PREFIX   = "ddos:attempts:";
    private static final Duration ATTEMPT_WINDOW  = Duration.ofMinutes(10);
    private static final int ESCALATION_THRESHOLD = 3;

    private static final DefaultRedisScript<Long> INCR_WITH_EXPIRE = new DefaultRedisScript<>(
            "local c = redis.call('INCR', KEYS[1])\n" +
            "if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end\n" +
            "return c",
            Long.class
    );

    private final IpBlockService ipBlockService;
    private final RateLimitService rateLimitService;
    private final RedisTemplate<String, String> redisTemplate;

    public DDoSReactionService(AlertService alertService, ReactionLogService reactionLogService,
                               IpBlockService ipBlockService, RateLimitService rateLimitService,
                               RedisTemplate<String, String> redisTemplate) {
        super(DetectionType.DDOS, alertService, reactionLogService);
        this.ipBlockService = ipBlockService;
        this.rateLimitService = rateLimitService;
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected Alert buildAlertDto(ReactionInput input) {
        DDoSInput ddos = (DDoSInput) input;
        return DDoSAlert.builder()
                .detectionType(ddos.getDetectionType())
                .sourceIp(ddos.getSourceIp())
                .severity(ddos.getSeverity())
                .detectedAt(ddos.getDetectedAt())
                .windowStart(ddos.getWindowStart())
                .windowEnd(ddos.getWindowEnd())
                .destIp(ddos.getDestIp())
                .destPort(ddos.getDestPort())
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
            log.info("DDoS from {} reached {} attempts in window — escalating to block", ip, attempts);
            ipBlockService.block(ip, input.getSeverity());
            return ReactionAction.BLOCK;
        } else {
            log.info("DDoS from {} at attempt {} — applying rate limit", ip, attempts);
            rateLimitService.limit(ip, input.getSeverity());
            return ReactionAction.RATE_LIMIT;
        }
    }
}
