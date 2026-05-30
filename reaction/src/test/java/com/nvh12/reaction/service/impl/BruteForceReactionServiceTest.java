package com.nvh12.reaction.service.impl;

import com.nvh12.reaction.service.AlertService;
import com.nvh12.reaction.service.IpBlockService;
import com.nvh12.reaction.service.RateLimitService;
import com.nvh12.reaction.service.ReactionLogService;
import com.nvh12.reaction.service.dto.BruteForceInput;
import com.nvh12.reaction.service.dto.DetectionType;
import com.nvh12.reaction.service.dto.ReactionAction;
import com.nvh12.reaction.service.dto.Severity;
import com.nvh12.reaction.service.dto.alert.BruteForceAlert;
import org.junit.jupiter.api.BeforeEach;
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

@ExtendWith(MockitoExtension.class)
class BruteForceReactionServiceTest {

    @Mock AlertService alertService;
    @Mock ReactionLogService reactionLogService;
    @Mock IpBlockService ipBlockService;
    @Mock RateLimitService rateLimitService;
    @Mock RedisTemplate<String, String> redisTemplate;

    BruteForceReactionService service;

    static final String ATTEMPTS_PREFIX   = "brute:attempts:";
    static final Duration ATTEMPT_WINDOW  = Duration.ofMinutes(10);
    static final int ESCALATION_THRESHOLD = 3;

    static final String IP           = "7.7.7.7";
    static final String ATTEMPTS_KEY = ATTEMPTS_PREFIX + IP;
    static final String WINDOW_SECS  = String.valueOf(ATTEMPT_WINDOW.toSeconds());

    @BeforeEach
    void setUp() {
        service = new BruteForceReactionService(alertService, reactionLogService, ipBlockService, rateLimitService, redisTemplate);
    }

    private BruteForceInput input(Severity severity) {
        BruteForceInput input = new BruteForceInput();
        input.setDetectionType(DetectionType.BRUTE_FORCE);
        input.setSourceIp(IP);
        input.setSeverity(severity);
        return input;
    }

    @Test
    void handle_firstAttempt_appliesRateLimit() {
        doReturn(1L).when(redisTemplate).execute(any(RedisScript.class), anyList(), any());
        BruteForceInput input = input(Severity.MEDIUM);

        service.handle(input);

        verify(rateLimitService).limit(IP, Severity.MEDIUM);
        verify(ipBlockService, never()).block(any(), any());
        verify(alertService).enqueue(any(BruteForceAlert.class));
        verify(reactionLogService).save(input, ReactionAction.RATE_LIMIT);
    }

    @Test
    void handle_firstAttempt_executesScriptWithWindowTtl() {
        doReturn(1L).when(redisTemplate).execute(any(RedisScript.class), anyList(), any());

        service.handle(input(Severity.MEDIUM));

        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of(ATTEMPTS_KEY)), eq(WINDOW_SECS));
    }

    @Test
    void handle_secondAttempt_appliesRateLimit() {
        doReturn(2L).when(redisTemplate).execute(any(RedisScript.class), anyList(), any());

        service.handle(input(Severity.MEDIUM));

        verify(rateLimitService).limit(IP, Severity.MEDIUM);
        verify(ipBlockService, never()).block(any(), any());
    }

    @Test
    void handle_secondAttempt_executesScriptWithSameWindow() {
        doReturn(2L).when(redisTemplate).execute(any(RedisScript.class), anyList(), any());

        service.handle(input(Severity.MEDIUM));

        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of(ATTEMPTS_KEY)), eq(WINDOW_SECS));
    }

    @Test
    void handle_atEscalationThreshold_escalatesToBlock() {
        doReturn((long) ESCALATION_THRESHOLD).when(redisTemplate).execute(any(RedisScript.class), anyList(), any());
        BruteForceInput input = input(Severity.HIGH);

        service.handle(input);

        verify(ipBlockService).block(IP, Severity.HIGH);
        verify(rateLimitService, never()).limit(any(), any());
        verify(alertService).enqueue(any(BruteForceAlert.class));
        verify(reactionLogService).save(input, ReactionAction.BLOCK);
    }

    @Test
    void handle_aboveEscalationThreshold_escalatesToBlock() {
        doReturn(10L).when(redisTemplate).execute(any(RedisScript.class), anyList(), any());

        service.handle(input(Severity.CRITICAL));

        verify(ipBlockService).block(IP, Severity.CRITICAL);
        verify(rateLimitService, never()).limit(any(), any());
    }

    @Test
    void handle_nullScriptResult_treatedAsFirstAttempt() {
        doReturn(null).when(redisTemplate).execute(any(RedisScript.class), anyList(), any());
        BruteForceInput input = input(Severity.MEDIUM);

        service.handle(input);

        verify(rateLimitService).limit(IP, Severity.MEDIUM);
        verify(ipBlockService, never()).block(any(), any());
        verify(reactionLogService).save(input, ReactionAction.RATE_LIMIT);
    }
}
