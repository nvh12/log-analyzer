package com.nvh12.reaction.service.impl;

import com.nvh12.reaction.service.AlertService;
import com.nvh12.reaction.service.IpBlockService;
import com.nvh12.reaction.service.RateLimitService;
import com.nvh12.reaction.service.ReactionLogService;
import com.nvh12.reaction.service.dto.DDoSInput;
import com.nvh12.reaction.service.dto.DetectionType;
import com.nvh12.reaction.service.dto.ReactionAction;
import com.nvh12.reaction.service.dto.Severity;
import com.nvh12.reaction.service.dto.alert.DDoSAlert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DDoSReactionServiceTest {

    @Mock AlertService alertService;
    @Mock ReactionLogService reactionLogService;
    @Mock IpBlockService ipBlockService;
    @Mock RateLimitService rateLimitService;
    @Mock RedisTemplate<String, String> redisTemplate;

    DDoSReactionService service;

    @BeforeEach
    void setUp() {
        service = new DDoSReactionService(alertService, reactionLogService,
                ipBlockService, rateLimitService, redisTemplate);
    }

    @Test
    void handle_firstAttempt_appliesRateLimit() {
        when(redisTemplate.execute(any(), any(), any())).thenReturn(1L);
        DDoSInput input = input("5.5.5.5", Severity.HIGH);

        service.handle(input);

        verify(rateLimitService).limit("5.5.5.5", Severity.HIGH);
        verify(ipBlockService, never()).block(any(), any());
        verify(reactionLogService).save(input, ReactionAction.RATE_LIMIT);
        verify(alertService).enqueue(any(DDoSAlert.class));
    }

    @Test
    void handle_atEscalationThreshold_blocksIp() {
        when(redisTemplate.execute(any(), any(), any())).thenReturn(3L);
        DDoSInput input = input("5.5.5.5", Severity.CRITICAL);

        service.handle(input);

        verify(ipBlockService).block("5.5.5.5", Severity.CRITICAL);
        verify(rateLimitService, never()).limit(any(), any());
        verify(reactionLogService).save(input, ReactionAction.BLOCK);
        verify(alertService).enqueue(any(DDoSAlert.class));
    }

    @Test
    void handle_aboveEscalationThreshold_blocksIp() {
        when(redisTemplate.execute(any(), any(), any())).thenReturn(5L);
        DDoSInput input = input("5.5.5.5", Severity.CRITICAL);

        service.handle(input);

        verify(ipBlockService).block("5.5.5.5", Severity.CRITICAL);
        verify(rateLimitService, never()).limit(any(), any());
        verify(reactionLogService).save(input, ReactionAction.BLOCK);
    }

    @Test
    void handle_nullCounterResult_treatedAsFirstAttempt() {
        when(redisTemplate.execute(any(), any(), any())).thenReturn(null);
        DDoSInput input = input("5.5.5.5", Severity.MEDIUM);

        service.handle(input);

        verify(rateLimitService).limit("5.5.5.5", Severity.MEDIUM);
        verify(ipBlockService, never()).block(any(), any());
    }

    private DDoSInput input(String ip, Severity severity) {
        DDoSInput input = new DDoSInput();
        input.setDetectionType(DetectionType.DDOS);
        input.setSourceIp(ip);
        input.setSeverity(severity);
        return input;
    }
}
