package com.nvh12.log_processing.presentation;

import com.nvh12.log_processing.AbstractContainerIT;
import com.nvh12.log_processing.domain.model.RawLog;
import com.nvh12.log_processing.infrastructure.config.RabbitMqConfig;
import com.nvh12.log_processing.infrastructure.polling.LogProcessingPoller;
import com.nvh12.log_processing.infrastructure.service.RedisQueueService;
import com.nvh12.log_processing.presentation.DeadLetterConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;

class RawLogConsumerIT extends AbstractContainerIT {
    static final String QUEUE_KEY = "raw-log-queue";

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @MockitoBean
    private LogProcessingPoller poller;

    @MockitoBean
    private DeadLetterConsumer deadLetterConsumer;


    @Test
    void publishValidRawLog_appearsInRedis() {
        RawLog rawLog = RawLog.builder().id("id-raw-1").source("src").rawMessage("msg").receivedAt(Instant.now()).build();
        rabbitTemplate.convertAndSend(RabbitMqConfig.QUEUE_RAW, rawLog);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Long size = redisTemplate.opsForZSet().zCard(QUEUE_KEY);
            assertThat(size).isEqualTo(1L);
        });
    }

    @Test
    void publishMalformedJson_deadLetters() {
        rabbitTemplate.send(RabbitMqConfig.QUEUE_RAW, 
                org.springframework.amqp.core.MessageBuilder.withBody("invalid-json".getBytes()).build());

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Message received = rabbitTemplate.receive(RabbitMqConfig.QUEUE_RAW_DLQ);
            assertThat(received).isNotNull();
        });
    }
}
