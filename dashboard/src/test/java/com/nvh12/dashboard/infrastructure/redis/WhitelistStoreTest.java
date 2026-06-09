package com.nvh12.dashboard.infrastructure.redis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WhitelistStoreTest {

    @Mock RedisTemplate<String, String> redisTemplate;
    @Mock SetOperations<String, String> setOps;
    @InjectMocks WhitelistStore store;

    @Test
    void listWhitelistedIps_nullSet_returnsEmpty() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.members(WhitelistStore.WHITELIST_IPS)).thenReturn(null);

        assertThat(store.listWhitelistedIps()).isEmpty();
    }

    @Test
    void listWhitelistedIps_returnsMembers() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.members(WhitelistStore.WHITELIST_IPS)).thenReturn(Set.of("1.2.3.4", "5.6.7.8"));

        assertThat(store.listWhitelistedIps()).containsExactlyInAnyOrder("1.2.3.4", "5.6.7.8");
    }

    @Test
    void replaceWhitelist_deletesAndAddsAll() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);

        store.replaceWhitelist(List.of("1.2.3.4", "5.6.7.8"));

        verify(redisTemplate).delete(WhitelistStore.WHITELIST_IPS);
        verify(setOps).add(WhitelistStore.WHITELIST_IPS, "1.2.3.4", "5.6.7.8");
    }

    @Test
    void replaceWhitelist_emptyList_onlyDeletes() {
        store.replaceWhitelist(List.of());

        verify(redisTemplate).delete(WhitelistStore.WHITELIST_IPS);
        verifyNoInteractions(setOps);
    }

    @Test
    void listWhitelistedIps_returnsImmutableList() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.members(WhitelistStore.WHITELIST_IPS)).thenReturn(Set.of("1.2.3.4"));

        List<String> result = store.listWhitelistedIps();

        assertThat(result).hasSize(1);
        assertThatThrownBy(() -> result.add("9.9.9.9")).isInstanceOf(UnsupportedOperationException.class);
    }
}
