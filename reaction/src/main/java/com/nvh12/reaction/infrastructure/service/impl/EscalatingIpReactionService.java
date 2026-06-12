package com.nvh12.reaction.infrastructure.service.impl;

import com.nvh12.reaction.service.AlertService;
import com.nvh12.reaction.service.IpBlockService;
import com.nvh12.reaction.service.RateLimitService;
import com.nvh12.reaction.service.ReactionLogService;
import com.nvh12.reaction.service.ReactionService;
import com.nvh12.reaction.service.dto.DetectionType;
import com.nvh12.reaction.service.dto.ReactionAction;
import com.nvh12.reaction.service.dto.ReactionInput;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;

@Slf4j
abstract class EscalatingIpReactionService extends ReactionService {

    private static final DefaultRedisScript<Long> INCR_WITH_EXPIRE = new DefaultRedisScript<>(
            "local c = redis.call('INCR', KEYS[1])\n" +
                    "if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end\n" +
                    "return c",
            Long.class
    );

    private static final Duration ATTEMPT_WINDOW = Duration.ofMinutes(10);
    private static final int ESCALATION_THRESHOLD = 3;

    private final IpBlockService ipBlockService;
    private final RateLimitService rateLimitService;
    private final RedisTemplate<String, String> redisTemplate;

    protected EscalatingIpReactionService(DetectionType type, AlertService alertService,
                                          ReactionLogService reactionLogService,
                                          IpBlockService ipBlockService,
                                          RateLimitService rateLimitService,
                                          RedisTemplate<String, String> redisTemplate) {
        super(type, alertService, reactionLogService);
        this.ipBlockService = ipBlockService;
        this.rateLimitService = rateLimitService;
        this.redisTemplate = redisTemplate;
    }

    protected abstract String attemptsKeyPrefix();

    @Override
    protected final ReactionAction doHandle(ReactionInput input) {
        String ip = input.getSourceIp();
        String attemptsKey = attemptsKeyPrefix() + ip;

        Long attempts = redisTemplate.execute(INCR_WITH_EXPIRE,
                List.of(attemptsKey), String.valueOf(ATTEMPT_WINDOW.toSeconds()));
        if (attempts == null) attempts = 1L;

        if (attempts >= ESCALATION_THRESHOLD) {
            log.info("{} from {} reached {} attempts in window — escalating to block",
                    getType(), ip, attempts);
            ipBlockService.block(ip, input.getSeverity());
            return ReactionAction.BLOCK;
        } else {
            log.info("{} from {} at attempt {} — applying rate limit", getType(), ip, attempts);
            rateLimitService.limit(ip, input.getSeverity());
            return ReactionAction.RATE_LIMIT;
        }
    }
}
