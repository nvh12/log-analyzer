package com.nvh12.log_processing.presentation;

import com.nvh12.log_processing.AbstractContainerIT;
import com.nvh12.log_processing.application.DlqRetryScheduler;
import com.nvh12.log_processing.infrastructure.config.RabbitMqConfig;
import com.nvh12.log_processing.infrastructure.polling.LogProcessingPoller;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;

class DeadLetterConsumerIT extends AbstractContainerIT {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private LogProcessingPoller poller;

    @MockitoBean
    private DlqRetryScheduler retryScheduler;

    @Test
    void sendDeadLetter_persistsToDropAudit() {
        String json = "{\"id\":\"id-dlq-test\", \"message\":\"fail\"}";
        Message message = MessageBuilder.withBody(json.getBytes(StandardCharsets.UTF_8))
                .setHeader("x-death", List.of(Map.of("reason", "expired")))
                .build();

        rabbitTemplate.send(RabbitMqConfig.QUEUE_RAW_DLQ, message);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM log_processing.drop_audit WHERE log_id = 'id-dlq-test'", Integer.class);
            assertThat(count).isEqualTo(1);
        });
    }

    @Test
    void sendDeadLetterNoId_persistsToDropAudit() {
        String json = "{\"message\":\"no-id\"}";
        rabbitTemplate.send(RabbitMqConfig.QUEUE_RAW_DLQ,
                MessageBuilder.withBody(json.getBytes(StandardCharsets.UTF_8)).build());

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM log_processing.drop_audit WHERE drop_reason = 'DEAD_LETTERED'", Integer.class);
            assertThat(count).isPositive();
        });
    }
}
