package com.nvh12.reaction.infrastructure.service.impl;

import com.nvh12.reaction.AbstractContainerIT;
import com.nvh12.reaction.service.dto.Severity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class RedisRateLimitServiceIT extends AbstractContainerIT {

    @Autowired RedisRateLimitService service;
    @Autowired StringRedisTemplate redisTemplate;

    private static final String WHITELIST_IPS   = "whitelist:ips";
    private static final String COUNTER_PREFIX  = "ratelimit:ip:";
    private static final String LIMIT_SUFFIX    = ":limit";

    @Test
    void limit_setsCounterAndLimitKeys() {
        service.limit("1.2.3.4", Severity.MEDIUM);

        String counterKey = COUNTER_PREFIX + "1.2.3.4";
        String limitKey   = counterKey + LIMIT_SUFFIX;

        assertThat(redisTemplate.hasKey(counterKey)).isTrue();
        assertThat(redisTemplate.opsForValue().get(counterKey)).isEqualTo("0");
        assertThat(redisTemplate.hasKey(limitKey)).isTrue();
        assertThat(redisTemplate.opsForValue().get(limitKey)).isEqualTo("10");
    }

    @Test
    void limit_whenWhitelisted_writesNothing() {
        redisTemplate.opsForSet().add(WHITELIST_IPS, "1.2.3.4");

        service.limit("1.2.3.4", Severity.HIGH);

        assertThat(redisTemplate.hasKey(COUNTER_PREFIX + "1.2.3.4")).isFalse();
    }

    @Test
    void limit_requestsPerMinute_matchesSeverity() {
        service.limit("a", Severity.LOW);
        service.limit("b", Severity.HIGH);
        service.limit("c", Severity.CRITICAL);

        assertThat(redisTemplate.opsForValue().get(COUNTER_PREFIX + "a" + LIMIT_SUFFIX)).isEqualTo("30");
        assertThat(redisTemplate.opsForValue().get(COUNTER_PREFIX + "b" + LIMIT_SUFFIX)).isEqualTo("3");
        assertThat(redisTemplate.opsForValue().get(COUNTER_PREFIX + "c" + LIMIT_SUFFIX)).isEqualTo("1");
    }

    @Test
    void isLimited_afterLimit_returnsTrue() {
        service.limit("1.2.3.4", Severity.LOW);

        assertThat(service.isLimited("1.2.3.4")).isTrue();
    }

    @Test
    void isLimited_withoutPriorLimit_returnsFalse() {
        assertThat(service.isLimited("9.9.9.9")).isFalse();
    }
}
