package com.nvh12.reaction.service.impl;

import com.nvh12.reaction.service.dto.Severity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisRateLimitServiceTest {

    @Mock RedisTemplate<String, String> redisTemplate;
    @Mock SetOperations<String, String> setOps;

    RedisRateLimitService service;

    private static final String WHITELIST_IPS    = "whitelist:ips";
    private static final String COUNTER_PREFIX   = "ratelimit:ip:";
    private static final String LIMIT_SUFFIX     = ":limit";
    private static final String WINDOW_END_SUFFIX = ":window_end";

    @BeforeEach
    void setUp() {
        service = new RedisRateLimitService(redisTemplate);
    }

    @Test
    void limit_whenWhitelisted_skips() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.isMember(WHITELIST_IPS, "1.2.3.4")).thenReturn(true);

        service.limit("1.2.3.4", Severity.MEDIUM);

        verify(redisTemplate, never()).execute(any(RedisScript.class), anyList(), any(), any(), any());
    }

    @Test
    void limit_setsRateLimitAtomically() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.isMember(WHITELIST_IPS, "1.2.3.4")).thenReturn(false);

        service.limit("1.2.3.4", Severity.MEDIUM);

        // MEDIUM: 10 rpm, 30 min policy ttl (1800s), 60s window
        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of(COUNTER_PREFIX + "1.2.3.4" + LIMIT_SUFFIX, COUNTER_PREFIX + "1.2.3.4", COUNTER_PREFIX + "1.2.3.4" + WINDOW_END_SUFFIX)),
                eq("10"), eq("1800"), eq("60")
        );
    }

    @Test
    void limit_requestsPerMinute_variesBySeverity() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);

        service.limit("a", Severity.LOW);
        service.limit("b", Severity.HIGH);
        service.limit("c", Severity.CRITICAL);

        verify(redisTemplate).execute(any(), eq(List.of(COUNTER_PREFIX + "a" + LIMIT_SUFFIX, COUNTER_PREFIX + "a", COUNTER_PREFIX + "a" + WINDOW_END_SUFFIX)), eq("30"), any(), any());
        verify(redisTemplate).execute(any(), eq(List.of(COUNTER_PREFIX + "b" + LIMIT_SUFFIX, COUNTER_PREFIX + "b", COUNTER_PREFIX + "b" + WINDOW_END_SUFFIX)), eq("3"), any(), any());
        verify(redisTemplate).execute(any(), eq(List.of(COUNTER_PREFIX + "c" + LIMIT_SUFFIX, COUNTER_PREFIX + "c", COUNTER_PREFIX + "c" + WINDOW_END_SUFFIX)), eq("1"), any(), any());
    }

    @Test
    void isLimited_whenLimitKeyExists_returnsTrue() {
        when(redisTemplate.hasKey(COUNTER_PREFIX + "1.2.3.4" + LIMIT_SUFFIX)).thenReturn(true);

        assertThat(service.isLimited("1.2.3.4")).isTrue();
    }

    @Test
    void isLimited_whenLimitKeyMissing_returnsFalse() {
        when(redisTemplate.hasKey(COUNTER_PREFIX + "1.2.3.4" + LIMIT_SUFFIX)).thenReturn(false);

        assertThat(service.isLimited("1.2.3.4")).isFalse();
    }

    @Test
    void isLimited_whenRedisThrows_failsOpen() {
        when(redisTemplate.hasKey(any())).thenThrow(new QueryTimeoutException("timeout"));

        assertThat(service.isLimited("1.2.3.4")).isFalse();
    }
}
