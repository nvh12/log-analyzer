package com.nvh12.reaction.service.impl;

import com.nvh12.reaction.AbstractContainerIT;
import com.nvh12.reaction.service.dto.DetectionType;
import com.nvh12.reaction.service.dto.Severity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class RedisScaleServiceIT extends AbstractContainerIT {

    @Autowired RedisScaleService service;
    @Autowired StringRedisTemplate redisTemplate;

    private static final String SCALE_STATE    = "scale:state";
    private static final String SCALE_REPLICAS = "scale:replicas";

    @Test
    void requestScale_setsScaledUpState() {
        service.requestScale(DetectionType.DDOS, Severity.HIGH);

        assertThat(redisTemplate.opsForValue().get(SCALE_STATE)).isEqualTo("scaled_up");
    }

    @Test
    void requestScale_withoutConfig_writesDefaultReplicas() {
        service.requestScale(DetectionType.TRAFFIC, Severity.HIGH);

        assertThat(redisTemplate.opsForValue().get(SCALE_REPLICAS)).isEqualTo("5");
    }

    @Test
    void requestScale_withConfigKey_writesConfiguredReplicas() {
        redisTemplate.opsForValue().set("config:scale:replicas:HIGH", "12");

        service.requestScale(DetectionType.DDOS, Severity.HIGH);

        assertThat(redisTemplate.opsForValue().get(SCALE_REPLICAS)).isEqualTo("12");
    }

    @Test
    void requestScale_defaultReplicas_varyBySeverity() {
        service.requestScale(DetectionType.TRAFFIC, Severity.LOW);
        assertThat(redisTemplate.opsForValue().get(SCALE_REPLICAS)).isEqualTo("2");

        service.requestScale(DetectionType.TRAFFIC, Severity.MEDIUM);
        assertThat(redisTemplate.opsForValue().get(SCALE_REPLICAS)).isEqualTo("3");

        service.requestScale(DetectionType.TRAFFIC, Severity.CRITICAL);
        assertThat(redisTemplate.opsForValue().get(SCALE_REPLICAS)).isEqualTo("8");
    }
}
