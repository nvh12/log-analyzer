package com.nvh12.log_processing.application;

import com.nvh12.log_processing.AbstractContainerIT;
import com.nvh12.log_processing.domain.model.FailedLogEntry;
import com.nvh12.log_processing.domain.model.RawLog;
import com.nvh12.log_processing.domain.service.FailedLogRepository;
import com.nvh12.log_processing.infrastructure.config.RabbitMqConfig;
import com.nvh12.log_processing.infrastructure.persistence.repository.RedisDlqRepository;
import com.nvh12.log_processing.infrastructure.polling.LogProcessingPoller;
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

class DlqRetrySchedulerIT extends AbstractContainerIT {

    @Autowired
    private DlqRetryScheduler retryScheduler;

    @Autowired
    private FailedLogRepository failedLogRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @MockitoBean
    private LogProcessingPoller poller;

    @BeforeEach
    void cleanUp() {
        redisTemplate.delete("failed-log-queue");
        jdbcTemplate.execute("DELETE FROM normalized_http");
        jdbcTemplate.execute("DELETE FROM drop_audit");
    }

    @Test
    void retrySuccessful_persistsAndPublishes() {
        // Seed Redis DLQ with a valid HTTP log (CLF format)
        String clf = "127.0.0.1 - - [03/May/2026:12:00:00 +0000] \"GET /index.html HTTP/1.1\" 200 1024";
        RawLog raw = RawLog.builder().id("id-retry-ok").source("web").rawMessage(clf).receivedAt(Instant.now()).build();
        failedLogRepository.save(raw, "first-fail");

        retryScheduler.retryFailedLogs();

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM normalized_http WHERE ip = '127.0.0.1'", Integer.class);
            assertThat(count).isEqualTo(1);
            
            Object received = rabbitTemplate.receiveAndConvert(RabbitMqConfig.QUEUE_NORMALIZED_HTTP);
            assertThat(received).isNotNull();
        });
    }

    @Test
    void retryExhausted_persistsToDropAudit() {
        // Seed DLQ with a log at retryCount == maxRetries (default is 3)
        RawLog raw = RawLog.builder().id("id-exhausted").source("src").rawMessage("msg").receivedAt(Instant.now()).build();
        FailedLogEntry entry = FailedLogEntry.builder()
                .rawLog(raw)
                .failureReason("too many fails")
                .failedAt(Instant.now())
                .retryCount(3)
                .build();
        
        failedLogRepository.saveEntry(entry);

        retryScheduler.retryFailedLogs();

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM drop_audit WHERE log_id = 'id-exhausted' AND drop_reason = 'RETRY_EXHAUSTED'", Integer.class);
            assertThat(count).isEqualTo(1);
            
            // Should be removed from DLQ
            assertThat(failedLogRepository.getFailedLogEntries(10)).isEmpty();
        });
    }
}
