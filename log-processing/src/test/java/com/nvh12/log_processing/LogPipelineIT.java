package com.nvh12.log_processing;

import com.nvh12.log_processing.application.DlqRetryScheduler;
import com.nvh12.log_processing.domain.model.RawLog;
import com.nvh12.log_processing.infrastructure.config.RabbitMqConfig;
import com.nvh12.log_processing.infrastructure.service.RedisQueueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;

class LogPipelineIT extends AbstractContainerIT {
    static final String QUEUE_KEY = "raw-log-queue";

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @MockitoBean
    private DlqRetryScheduler dlqRetryScheduler; // Disable retry scheduler to avoid noise


    @Test
    void fullHttpPipeline_success() {
        String clf = "192.168.1.1 - - [03/May/2026:15:00:00 +0000] \"POST /login HTTP/1.1\" 401 256";
        RawLog raw = RawLog.builder().id("id-full-1").source("auth-service").rawMessage(clf).receivedAt(Instant.now()).build();

        rabbitTemplate.convertAndSend(RabbitMqConfig.QUEUE_RAW, raw);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Integer dbCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM normalized_http WHERE ip = '192.168.1.1'", Integer.class);
            assertThat(dbCount).isEqualTo(1);

            Object received = rabbitTemplate.receiveAndConvert(RabbitMqConfig.QUEUE_NORMALIZED_HTTP);
            assertThat(received).isNotNull();
        });
    }

    @Test
    void fullFlowPipeline_success() {
        String flowJson = "{\"timestamp\":1714730000,\"source_ip\":\"10.0.0.5\",\"dest_ip\":\"10.0.0.6\",\"source_port\":54321,\"dest_port\":80,\"features\":{\"f1\":0.9}}";
        RawLog raw = RawLog.builder().id("id-full-flow").source("flow").rawMessage(flowJson).receivedAt(Instant.now()).build();

        rabbitTemplate.convertAndSend(RabbitMqConfig.QUEUE_RAW, raw);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Integer dbCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM normalized_flow WHERE source_ip = '10.0.0.5'", Integer.class);
            assertThat(dbCount).isEqualTo(1);
        });
    }
}
