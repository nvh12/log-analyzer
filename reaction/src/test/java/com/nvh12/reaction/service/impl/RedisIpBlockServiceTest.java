package com.nvh12.reaction.service.impl;

import com.nvh12.reaction.service.dto.Severity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import org.springframework.dao.QueryTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisIpBlockServiceTest {

    @Mock RedisTemplate<String, String> redisTemplate;
    @Mock SetOperations<String, String> setOps;
    @Mock ValueOperations<String, String> valueOps;

    RedisIpBlockService service;

    private static final String WHITELIST_IPS        = "whitelist:ips";
    private static final String BLOCKLIST_IPS        = "blocklist:ips";
    private static final String BLOCKLIST_IP_PREFIX  = "blocklist:ip:";

    @BeforeEach
    void setUp() {
        service = new RedisIpBlockService(redisTemplate);
    }

    @Test
    void block_whenWhitelisted_skips() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.isMember(WHITELIST_IPS, "1.2.3.4")).thenReturn(true);

        service.block("1.2.3.4", Severity.HIGH);

        verify(setOps, never()).add(eq(BLOCKLIST_IPS), any());
        verify(valueOps, never()).set(any(), any(), any(Duration.class));
    }

    @Test
    void block_whenNotWhitelisted_addsToSetAndSetsMetadataWithTtl() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(setOps.isMember(WHITELIST_IPS, "1.2.3.4")).thenReturn(false);

        service.block("1.2.3.4", Severity.HIGH);

        verify(setOps).add(BLOCKLIST_IPS, "1.2.3.4");
        verify(valueOps).set(
                eq(BLOCKLIST_IP_PREFIX + "1.2.3.4"),
                contains("HIGH"),
                eq(Duration.ofHours(2))
        );
    }

    @Test
    void block_ttl_variesBySeverity() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(setOps.isMember(WHITELIST_IPS, "1.2.3.4")).thenReturn(false);

        service.block("1.2.3.4", Severity.LOW);
        service.block("1.2.3.4", Severity.MEDIUM);
        service.block("1.2.3.4", Severity.CRITICAL);

        verify(valueOps).set(any(), any(), eq(Duration.ofMinutes(5)));
        verify(valueOps).set(any(), any(), eq(Duration.ofMinutes(30)));
        verify(valueOps).set(any(), any(), eq(Duration.ofHours(24)));
    }

    @Test
    void isBlocked_whenKeyExists_returnsTrue() {
        when(redisTemplate.hasKey(BLOCKLIST_IP_PREFIX + "1.2.3.4")).thenReturn(true);

        assertThat(service.isBlocked("1.2.3.4")).isTrue();
        verify(setOps, never()).remove(any(), any());
    }

    @Test
    void isBlocked_whenKeyMissing_returnsFalseAndRemovesFromSet() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(redisTemplate.hasKey(BLOCKLIST_IP_PREFIX + "1.2.3.4")).thenReturn(false);

        assertThat(service.isBlocked("1.2.3.4")).isFalse();
        verify(setOps).remove(BLOCKLIST_IPS, "1.2.3.4");
    }

    @Test
    void isBlocked_whenRedisThrows_failsOpen() {
        when(redisTemplate.hasKey(any())).thenThrow(new QueryTimeoutException("timeout"));

        assertThat(service.isBlocked("1.2.3.4")).isFalse();
    }
}
