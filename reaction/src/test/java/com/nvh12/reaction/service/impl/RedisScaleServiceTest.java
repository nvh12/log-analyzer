package com.nvh12.reaction.service.impl;

import com.nvh12.reaction.service.dto.DetectionType;
import com.nvh12.reaction.service.dto.Severity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisScaleServiceTest {

    @Mock RedisTemplate<String, String> redisTemplate;
    @Mock ValueOperations<String, String> valueOps;

    RedisScaleService service;

    private static final String SCALE_STATE    = "scale:state";
    private static final String SCALE_REPLICAS = "scale:replicas";

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        service = new RedisScaleService(redisTemplate);
    }

    @Test
    void requestScale_whenConfigPresent_usesConfiguredReplicas() {
        when(valueOps.get("config:scale:replicas:HIGH")).thenReturn("10");

        service.requestScale(DetectionType.DDOS, Severity.HIGH);

        verify(valueOps).set(eq(SCALE_REPLICAS), eq("10"), eq(Duration.ofHours(2)));
    }

    @Test
    void requestScale_whenConfigAbsent_usesDefaultReplicas() {
        when(valueOps.get("config:scale:replicas:HIGH")).thenReturn(null);

        service.requestScale(DetectionType.DDOS, Severity.HIGH);

        verify(valueOps).set(eq(SCALE_REPLICAS), eq("5"), eq(Duration.ofHours(2)));
    }

    @Test
    void requestScale_whenConfigInvalid_usesDefaultReplicas() {
        when(valueOps.get("config:scale:replicas:CRITICAL")).thenReturn("not-a-number");

        service.requestScale(DetectionType.TRAFFIC, Severity.CRITICAL);

        verify(valueOps).set(eq(SCALE_REPLICAS), eq("8"), eq(Duration.ofHours(24)));
    }

    @Test
    void requestScale_setsScaleStateWithSeverityTtl() {
        when(valueOps.get(anyString())).thenReturn(null);

        service.requestScale(DetectionType.DDOS, Severity.LOW);
        service.requestScale(DetectionType.DDOS, Severity.MEDIUM);
        service.requestScale(DetectionType.DDOS, Severity.HIGH);
        service.requestScale(DetectionType.DDOS, Severity.CRITICAL);

        verify(valueOps).set(eq(SCALE_STATE), eq("scaled_up"), eq(Duration.ofMinutes(5)));
        verify(valueOps).set(eq(SCALE_STATE), eq("scaled_up"), eq(Duration.ofMinutes(30)));
        verify(valueOps).set(eq(SCALE_STATE), eq("scaled_up"), eq(Duration.ofHours(2)));
        verify(valueOps).set(eq(SCALE_STATE), eq("scaled_up"), eq(Duration.ofHours(24)));
    }

    @Test
    void requestScale_defaultReplicas_varyBySeverity() {
        when(valueOps.get(anyString())).thenReturn(null);

        service.requestScale(DetectionType.TRAFFIC, Severity.LOW);
        service.requestScale(DetectionType.TRAFFIC, Severity.MEDIUM);
        service.requestScale(DetectionType.TRAFFIC, Severity.HIGH);
        service.requestScale(DetectionType.TRAFFIC, Severity.CRITICAL);

        verify(valueOps).set(eq(SCALE_REPLICAS), eq("2"), eq(Duration.ofMinutes(5)));
        verify(valueOps).set(eq(SCALE_REPLICAS), eq("3"), eq(Duration.ofMinutes(30)));
        verify(valueOps).set(eq(SCALE_REPLICAS), eq("5"), eq(Duration.ofHours(2)));
        verify(valueOps).set(eq(SCALE_REPLICAS), eq("8"), eq(Duration.ofHours(24)));
    }
}
