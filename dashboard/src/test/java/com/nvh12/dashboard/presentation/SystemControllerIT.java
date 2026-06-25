package com.nvh12.dashboard.presentation;

import com.nvh12.dashboard.AbstractContainerIT;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SystemControllerIT extends AbstractContainerIT {

    @Autowired RedisTemplate<String, String> redisTemplate;
    @Autowired AmqpAdmin amqpAdmin;
    @Autowired RabbitTemplate rabbitTemplate;

    @Test
    void health_returnsExpectedTopLevelStructure() throws Exception {
        mockMvc.perform(get("/api/system/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queue_depths").isArray())
                .andExpect(jsonPath("$.workers").isMap())
                .andExpect(jsonPath("$.workers.current").isNumber())
                .andExpect(jsonPath("$.workers.target").isNumber())
                .andExpect(jsonPath("$.workers.available").isNumber());
    }

    @Test
    void health_workerScale_fallsBackToDefaultsWhenSimulationAndRedisUnavailable() throws Exception {
        // No simulation service and no scale:* keys in the test env — current/target/min/max default to 0
        mockMvc.perform(get("/api/system/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workers.current").value(0))
                .andExpect(jsonPath("$.workers.min").value(0))
                .andExpect(jsonPath("$.workers.max").value(0));
    }

    @Test
    void health_workerScale_readsCurrentAndTargetFromRedis() throws Exception {
        redisTemplate.opsForValue().set("scale:current_workers", "3");
        redisTemplate.opsForValue().set("scale:replicas", "5");

        mockMvc.perform(get("/api/system/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workers.current").value(3))
                .andExpect(jsonPath("$.workers.target").value(5));
    }

    @Test
    void health_queueDepths_returnsMessageCountForTrackedQueueWithBacklog() throws Exception {
        amqpAdmin.declareQueue(new Queue("log.raw", false, false, false));
        rabbitTemplate.convertAndSend("", "log.raw", "test-message");

        await().atMost(15, SECONDS).pollInterval(1, SECONDS).untilAsserted(() ->
                mockMvc.perform(get("/api/system/health"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.queue_depths[?(@.name=='log.raw')].messages",
                                contains(greaterThanOrEqualTo(1))))
        );

        amqpAdmin.deleteQueue("log.raw");
    }

    @Test
    void config_detectionServiceUnavailable_returnsNullDetection() throws Exception {
        // No detection service in test env — controller catches exception and puts null
        mockMvc.perform(get("/api/system/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detection").value(nullValue()));
    }
}
