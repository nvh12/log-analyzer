package com.nvh12.dashboard.presentation;

import com.nvh12.dashboard.AbstractContainerIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SystemControllerIT extends AbstractContainerIT {

    @Autowired RedisTemplate<String, String> redisTemplate;

    @Test
    void health_returnsExpectedTopLevelStructure() throws Exception {
        mockMvc.perform(get("/api/system/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queue_depths").isArray())
                .andExpect(jsonPath("$.redis").isMap())
                .andExpect(jsonPath("$.redis.keyspace_hits").isNumber())
                .andExpect(jsonPath("$.redis.keyspace_misses").isNumber())
                .andExpect(jsonPath("$.redis.hit_rate").isNumber());
    }

    @Test
    void health_redisStats_hitRateIsZeroWhenNoActivity() throws Exception {
        // Fresh container, no keyspace activity yet
        mockMvc.perform(get("/api/system/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redis.hit_rate").value(0.0));
    }

    @Test
    void health_redisStats_recordsHitsAfterCacheReads() throws Exception {
        redisTemplate.opsForValue().set("test:key", "val", Duration.ofSeconds(30));
        redisTemplate.opsForValue().get("test:key"); // hit
        redisTemplate.opsForValue().get("test:key"); // hit

        mockMvc.perform(get("/api/system/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redis.keyspace_hits").value(greaterThan(0)));
    }

    @Test
    void health_queueDepths_returnsArrayEvenWhenNoTrackedQueuesPresent() throws Exception {
        // None of the tracked queues (log.raw, detection.results, etc.) exist in test container
        mockMvc.perform(get("/api/system/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queue_depths").isArray());
    }

    @Test
    void config_detectionServiceUnavailable_returnsNullDetection() throws Exception {
        // No detection service in test env — controller catches exception and puts null
        mockMvc.perform(get("/api/system/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detection").value(nullValue()));
    }
}
