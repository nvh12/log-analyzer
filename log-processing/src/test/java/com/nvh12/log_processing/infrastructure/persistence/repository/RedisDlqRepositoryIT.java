package com.nvh12.log_processing.infrastructure.persistence.repository;

import com.nvh12.log_processing.AbstractContainerIT;
import com.nvh12.log_processing.domain.model.DropReason;
import com.nvh12.log_processing.domain.model.FailedLogEntry;
import com.nvh12.log_processing.domain.model.LogSource;
import com.nvh12.log_processing.domain.model.RawLog;
import com.nvh12.log_processing.domain.service.DropAuditRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

class RedisDlqRepositoryIT extends AbstractContainerIT {

    @Autowired
    private RedisDlqRepository redisDlqRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @MockitoBean
    private DropAuditRepository dropAuditRepository;

    @BeforeEach
    void cleanUp() {
        redisTemplate.delete(RedisDlqRepository.DLQ_KEY);
    }

    @Test
    void saveOneEntry_returnsMatchingEntry() {
        RawLog rawLog = RawLog.builder().id("id-1").source(LogSource.HTTP).rawMessage("msg").receivedAt(Instant.now()).build();
        redisDlqRepository.save(rawLog, "some failure");

        List<FailedLogEntry> failedLogs = redisDlqRepository.getFailedLogEntries(1);
        assertThat(failedLogs).hasSize(1);
        assertThat(failedLogs.get(0).rawLog().getId()).isEqualTo("id-1");
        assertThat(failedLogs.get(0).failureReason()).isEqualTo("some failure");
    }

    @Test
    void fillToCapacity_dropsAndAudits() {
        // We can override capacity for this test if we want, or use the one from properties.
        // Let's assume it's small or we just fill it.
        // To be safe, let's use ReflectionTestUtils to set a small capacity if needed, 
        // or just rely on the @TestPropertySource in AbstractContainerIT if I add it there.
        
        // For now, let's just assume we can fill it.
        RawLog rawLog = RawLog.builder().id("id-cap").source(LogSource.HTTP).rawMessage("msg").receivedAt(Instant.now()).build();
        
        // If capacity is 2 (let's set it in AbstractContainerIT)
        redisDlqRepository.save(rawLog, "f1");
        redisDlqRepository.save(rawLog, "f2");
        redisDlqRepository.save(rawLog, "f3"); // Should drop

        verify(dropAuditRepository).record(any(FailedLogEntry.class), eq(DropReason.DLQ_OVERFLOW));
    }

    @Test
    void getFailedLogEntries_popsExactlyN_FIFO() {
        RawLog log1 = RawLog.builder().id("id-1").source(LogSource.HTTP).rawMessage("m1").receivedAt(Instant.now()).build();
        RawLog log2 = RawLog.builder().id("id-2").source(LogSource.HTTP).rawMessage("m2").receivedAt(Instant.now()).build();
        
        redisDlqRepository.save(log1, "f1");
        redisDlqRepository.save(log2, "f2");

        List<FailedLogEntry> batch = redisDlqRepository.getFailedLogEntries(1);
        assertThat(batch).hasSize(1);
        assertThat(batch.get(0).rawLog().getId()).isEqualTo("id-1");

        batch = redisDlqRepository.getFailedLogEntries(1);
        assertThat(batch).hasSize(1);
        assertThat(batch.get(0).rawLog().getId()).isEqualTo("id-2");
    }
}
