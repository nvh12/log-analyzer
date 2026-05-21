package com.nvh12.dashboard.infrastructure.redis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IpBlockStoreTest {

    @Mock RedisTemplate<String, String> redisTemplate;
    @Mock SetOperations<String, String> setOps;
    @Mock ValueOperations<String, String> valueOps;
    @InjectMocks IpBlockStore store;

    // ── listBlockedIps ──────────────────────────────────────────────────────

    @Test
    void listBlockedIps_nullSet_returnsEmpty() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.members(IpBlockStore.BLOCKLIST_IPS)).thenReturn(null);

        assertThat(store.listBlockedIps()).isEmpty();
    }

    @Test
    void listBlockedIps_emptySet_returnsEmpty() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.members(IpBlockStore.BLOCKLIST_IPS)).thenReturn(Set.of());

        assertThat(store.listBlockedIps()).isEmpty();
    }

    @Test
    void listBlockedIps_expiredKey_removesFromSetAndSkips() {
        String ip = "1.2.3.4";
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(setOps.members(IpBlockStore.BLOCKLIST_IPS)).thenReturn(Set.of(ip));
        when(valueOps.get(IpBlockStore.BLOCKLIST_IP_PREFIX + ip)).thenReturn(null);
        when(redisTemplate.getExpire(IpBlockStore.BLOCKLIST_IP_PREFIX + ip, TimeUnit.SECONDS)).thenReturn(-2L);

        List<Map<String, Object>> result = store.listBlockedIps();

        assertThat(result).isEmpty();
        verify(setOps).remove(IpBlockStore.BLOCKLIST_IPS, ip);
    }

    @Test
    void listBlockedIps_validKey_returnsEntryWithTtl() {
        String ip = "1.2.3.4";
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(setOps.members(IpBlockStore.BLOCKLIST_IPS)).thenReturn(Set.of(ip));
        when(valueOps.get(IpBlockStore.BLOCKLIST_IP_PREFIX + ip)).thenReturn("severity=HIGH");
        when(redisTemplate.getExpire(IpBlockStore.BLOCKLIST_IP_PREFIX + ip, TimeUnit.SECONDS)).thenReturn(1800L);

        List<Map<String, Object>> result = store.listBlockedIps();

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("ip", ip)
                .containsEntry("ttl_seconds", 1800L)
                .containsEntry("severity", "HIGH");
    }

    @Test
    void listBlockedIps_noMetaValue_returnsEntryWithoutMeta() {
        String ip = "5.5.5.5";
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(setOps.members(IpBlockStore.BLOCKLIST_IPS)).thenReturn(Set.of(ip));
        when(valueOps.get(IpBlockStore.BLOCKLIST_IP_PREFIX + ip)).thenReturn(null);
        when(redisTemplate.getExpire(IpBlockStore.BLOCKLIST_IP_PREFIX + ip, TimeUnit.SECONDS)).thenReturn(300L);

        List<Map<String, Object>> result = store.listBlockedIps();

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("ip", ip).containsEntry("ttl_seconds", 300L);
    }

    // ── listRateLimitedIps ──────────────────────────────────────────────────

    @Test
    void listRateLimitedIps_noKeys_returnsEmpty() {
        when(redisTemplate.keys(anyString())).thenReturn(null);

        assertThat(store.listRateLimitedIps()).isEmpty();
    }

    @Test
    void listRateLimitedIps_validKey_returnsEntry() {
        String ip = "2.2.2.2";
        String key = IpBlockStore.RATE_LIMIT_PREFIX + ip + IpBlockStore.RATE_LIMIT_SUFFIX;
        when(redisTemplate.keys(anyString())).thenReturn(Set.of(key));
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(key)).thenReturn("100");
        when(redisTemplate.getExpire(key, TimeUnit.SECONDS)).thenReturn(600L);

        List<Map<String, Object>> result = store.listRateLimitedIps();

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("ip", ip)
                .containsEntry("requests_per_minute", 100)
                .containsEntry("ttl_seconds", 600L);
    }

    @Test
    void listRateLimitedIps_malformedLimit_returnsNullRatherThanThrowing() {
        String ip = "3.3.3.3";
        String key = IpBlockStore.RATE_LIMIT_PREFIX + ip + IpBlockStore.RATE_LIMIT_SUFFIX;
        when(redisTemplate.keys(anyString())).thenReturn(Set.of(key));
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(key)).thenReturn("not-a-number");
        when(redisTemplate.getExpire(key, TimeUnit.SECONDS)).thenReturn(300L);

        assertThatNoException().isThrownBy(() -> {
            List<Map<String, Object>> result = store.listRateLimitedIps();
            assertThat(result).hasSize(1);
            assertThat(result.get(0).get("requests_per_minute")).isNull();
        });
    }

    @Test
    void listRateLimitedIps_expiredKey_skipsEntry() {
        String ip = "4.4.4.4";
        String key = IpBlockStore.RATE_LIMIT_PREFIX + ip + IpBlockStore.RATE_LIMIT_SUFFIX;
        when(redisTemplate.keys(anyString())).thenReturn(Set.of(key));
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(key)).thenReturn("50");
        when(redisTemplate.getExpire(key, TimeUnit.SECONDS)).thenReturn(-2L);

        assertThat(store.listRateLimitedIps()).isEmpty();
    }

    // ── liftBlock ───────────────────────────────────────────────────────────

    @Test
    void liftBlock_deletesKeyAndRemovesFromSet() {
        String ip = "9.9.9.9";
        when(redisTemplate.opsForSet()).thenReturn(setOps);

        store.liftBlock(ip);

        verify(redisTemplate).delete(IpBlockStore.BLOCKLIST_IP_PREFIX + ip);
        verify(setOps).remove(IpBlockStore.BLOCKLIST_IPS, ip);
    }
}
