package com.nvh12.reaction.infrastructure.service.impl;

import com.nvh12.reaction.AbstractContainerIT;
import com.nvh12.reaction.service.dto.Severity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class RedisIpBlockServiceIT extends AbstractContainerIT {

    @Autowired RedisIpBlockService service;
    @Autowired StringRedisTemplate redisTemplate;

    private static final String WHITELIST_IPS        = "whitelist:ips";
    private static final String BLOCKLIST_IPS        = "blocklist:ips";
    private static final String BLOCKLIST_IP_PREFIX  = "blocklist:ip:";

    @Test
    void block_addsIpToSetAndCreatesMetadataKey() {
        service.block("1.2.3.4", Severity.HIGH);

        assertThat(redisTemplate.opsForSet().isMember(BLOCKLIST_IPS, "1.2.3.4")).isTrue();
        assertThat(redisTemplate.hasKey(BLOCKLIST_IP_PREFIX + "1.2.3.4")).isTrue();
    }

    @Test
    void block_metadataContainsSeverity() {
        service.block("1.2.3.4", Severity.CRITICAL);

        String metadata = redisTemplate.opsForValue().get(BLOCKLIST_IP_PREFIX + "1.2.3.4");
        assertThat(metadata).contains("CRITICAL");
    }

    @Test
    void block_whenWhitelisted_writesNothing() {
        redisTemplate.opsForSet().add(WHITELIST_IPS, "1.2.3.4");

        service.block("1.2.3.4", Severity.HIGH);

        assertThat(redisTemplate.opsForSet().isMember(BLOCKLIST_IPS, "1.2.3.4")).isFalse();
        assertThat(redisTemplate.hasKey(BLOCKLIST_IP_PREFIX + "1.2.3.4")).isFalse();
    }

    @Test
    void isBlocked_afterBlock_returnsTrue() {
        service.block("1.2.3.4", Severity.MEDIUM);

        assertThat(service.isBlocked("1.2.3.4")).isTrue();
    }

    @Test
    void isBlocked_withoutPriorBlock_returnsFalse() {
        assertThat(service.isBlocked("9.9.9.9")).isFalse();
    }

    @Test
    void isBlocked_whenMetadataKeyExpiredManually_returnsFalseAndRemovesFromSet() {
        redisTemplate.opsForSet().add(BLOCKLIST_IPS, "1.2.3.4");
        // metadata key absent — simulates TTL expiry

        assertThat(service.isBlocked("1.2.3.4")).isFalse();
        assertThat(redisTemplate.opsForSet().isMember(BLOCKLIST_IPS, "1.2.3.4")).isFalse();
    }
}
