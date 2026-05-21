package com.nvh12.log_processing.infrastructure.service;

import com.nvh12.log_processing.AbstractContainerIT;
import com.nvh12.log_processing.domain.model.LogSource;
import com.nvh12.log_processing.domain.model.RawLog;
import com.nvh12.log_processing.domain.service.EventService;
import com.nvh12.log_processing.domain.service.FailedLogRepository;
import com.nvh12.log_processing.domain.service.LogProcessingService;
import com.nvh12.log_processing.domain.service.ProcessedLogRepository;
import com.nvh12.log_processing.infrastructure.polling.LogProcessingPoller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RedisQueueServiceIT extends AbstractContainerIT {

    @Autowired
    private RedisQueueService redisQueueService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @MockitoBean
    private LogProcessingPoller logProcessingPoller;

    @MockitoBean
    private LogProcessingService logProcessingService;

    @MockitoBean
    private EventService eventService;

    @MockitoBean
    private ProcessedLogRepository processedLogRepository;

    @MockitoBean
    private FailedLogRepository failedLogRepository;


    @Test
    void enqueueOneLog_returnsTrue() {
        RawLog rawLog = RawLog.builder()
                .id("id-1")
                .source(LogSource.HTTP)
                .rawMessage("message")
                .receivedAt(Instant.now())
                .build();
        boolean accepted = redisQueueService.enqueue(rawLog);
        
        assertThat(accepted).isTrue();
        assertThat(redisTemplate.opsForZSet().zCard(RedisQueueService.QUEUE_KEY)).isEqualTo(1L);
    }

    @Test
    void enqueueCapacityPlusOne_returnsFalse() {
        // AbstractContainerIT sets main-queue-capacity=10
        Instant now = Instant.now();
        for (int i = 0; i < 10; i++) {
            boolean accepted = redisQueueService.enqueue(RawLog.builder()
                    .id("id-cap-" + i).source(LogSource.HTTP).rawMessage("m").receivedAt(now.plusSeconds(i)).build());
            assertThat(accepted).isTrue();
        }

        boolean overflow = redisQueueService.enqueue(RawLog.builder()
                .id("id-overflow").source(LogSource.HTTP).rawMessage("m").receivedAt(now.plusSeconds(10)).build());

        assertThat(overflow).isFalse();
        assertThat(redisTemplate.opsForZSet().zCard(RedisQueueService.QUEUE_KEY)).isEqualTo(10L);
    }

    @Test
    void dequeueBatchAfterThreeEnqueues_returnsInOrder() {
        Instant now = Instant.now();
        RawLog log1 = RawLog.builder().id("id-1").source(LogSource.HTTP).rawMessage("m1").receivedAt(now.plusSeconds(10)).build();
        RawLog log2 = RawLog.builder().id("id-2").source(LogSource.HTTP).rawMessage("m2").receivedAt(now.plusSeconds(5)).build();
        RawLog log3 = RawLog.builder().id("id-3").source(LogSource.HTTP).rawMessage("m3").receivedAt(now.plusSeconds(15)).build();

        redisQueueService.enqueue(log1);
        redisQueueService.enqueue(log2);
        redisQueueService.enqueue(log3);

        List<RawLog> batch = redisQueueService.dequeueBatch(10);

        assertThat(batch).hasSize(3);
        assertThat(batch.get(0).getId()).isEqualTo("id-2"); // Earliest
        assertThat(batch.get(1).getId()).isEqualTo("id-1");
        assertThat(batch.get(2).getId()).isEqualTo("id-3"); // Latest
    }

    @Test
    void enqueueLogWithNullReceivedAt_returnsFalse() {
        RawLog rawLog = RawLog.builder()
                .id("id-1")
                .source(LogSource.HTTP)
                .rawMessage("message")
                .receivedAt(null)
                .build();
        boolean accepted = redisQueueService.enqueue(rawLog);
        
        assertThat(accepted).isFalse();
        assertThat(redisTemplate.opsForZSet().zCard(RedisQueueService.QUEUE_KEY)).isEqualTo(0L);
    }

    @Test
    void dequeueFromEmptyQueue_returnsEmptyList() {
        List<RawLog> batch = redisQueueService.dequeueBatch(10);
        assertThat(batch).isEmpty();
    }
}
