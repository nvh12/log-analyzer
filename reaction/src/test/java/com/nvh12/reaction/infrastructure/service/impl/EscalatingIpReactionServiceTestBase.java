package com.nvh12.reaction.infrastructure.service.impl;

import com.nvh12.reaction.service.AlertService;
import com.nvh12.reaction.service.IpBlockService;
import com.nvh12.reaction.service.RateLimitService;
import com.nvh12.reaction.service.ReactionLogService;
import com.nvh12.reaction.service.dto.ReactionAction;
import com.nvh12.reaction.service.dto.ReactionInput;
import com.nvh12.reaction.service.dto.Severity;
import com.nvh12.reaction.service.dto.alert.Alert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Shared escalation-threshold scenarios for {@link EscalatingIpReactionService} subclasses.
 * Concrete subclasses only need to wire up their specific service, input type, alert type
 * and Redis key prefix — the escalation logic itself (1st/2nd attempt -> rate limit,
 * 3rd+/null -> block) is identical across detection types and is verified once here.
 */
@ExtendWith(MockitoExtension.class)
abstract class EscalatingIpReactionServiceTestBase {

    static final Duration ATTEMPT_WINDOW = Duration.ofMinutes(10);
    static final int ESCALATION_THRESHOLD = 3;
    static final String IP = "7.7.7.7";

    @Mock AlertService alertService;
    @Mock ReactionLogService reactionLogService;
    @Mock IpBlockService ipBlockService;
    @Mock RateLimitService rateLimitService;
    @Mock RedisTemplate<String, String> redisTemplate;

    abstract EscalatingIpReactionService service();

    abstract ReactionInput input(Severity severity);

    abstract String attemptsKeyPrefix();

    abstract Class<? extends Alert> alertType();

    private String attemptsKey() {
        return attemptsKeyPrefix() + IP;
    }

    private String windowSeconds() {
        return String.valueOf(ATTEMPT_WINDOW.toSeconds());
    }

    @Test
    void handle_firstAttempt_appliesRateLimitAndExecutesScriptWithWindowTtl() {
        doReturn(1L).when(redisTemplate).execute(any(RedisScript.class), anyList(), any());
        ReactionInput input = input(Severity.MEDIUM);

        service().handle(input);

        verify(rateLimitService).limit(IP, Severity.MEDIUM);
        verify(ipBlockService, never()).block(any(), any());
        verify(alertService).enqueue(any(alertType()));
        verify(reactionLogService).save(input, ReactionAction.RATE_LIMIT);
        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of(attemptsKey())), eq(windowSeconds()));
    }

    @Test
    void handle_secondAttempt_stillAppliesRateLimit() {
        doReturn(2L).when(redisTemplate).execute(any(RedisScript.class), anyList(), any());

        service().handle(input(Severity.MEDIUM));

        verify(rateLimitService).limit(IP, Severity.MEDIUM);
        verify(ipBlockService, never()).block(any(), any());
    }

    @Test
    void handle_atEscalationThreshold_escalatesToBlock() {
        doReturn((long) ESCALATION_THRESHOLD).when(redisTemplate).execute(any(RedisScript.class), anyList(), any());
        ReactionInput input = input(Severity.HIGH);

        service().handle(input);

        verify(ipBlockService).block(IP, Severity.HIGH);
        verify(rateLimitService, never()).limit(any(), any());
        verify(alertService).enqueue(any(alertType()));
        verify(reactionLogService).save(input, ReactionAction.BLOCK);
    }

    @Test
    void handle_aboveEscalationThreshold_escalatesToBlock() {
        doReturn(10L).when(redisTemplate).execute(any(RedisScript.class), anyList(), any());

        service().handle(input(Severity.CRITICAL));

        verify(ipBlockService).block(IP, Severity.CRITICAL);
        verify(rateLimitService, never()).limit(any(), any());
    }

    @Test
    void handle_nullScriptResult_treatedAsFirstAttempt() {
        doReturn(null).when(redisTemplate).execute(any(RedisScript.class), anyList(), any());
        ReactionInput input = input(Severity.MEDIUM);

        service().handle(input);

        verify(rateLimitService).limit(IP, Severity.MEDIUM);
        verify(ipBlockService, never()).block(any(), any());
        verify(reactionLogService).save(input, ReactionAction.RATE_LIMIT);
    }
}
