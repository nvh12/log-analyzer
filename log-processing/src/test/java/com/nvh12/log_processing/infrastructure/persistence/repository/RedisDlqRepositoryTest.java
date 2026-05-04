package com.nvh12.log_processing.infrastructure.persistence.repository;

import com.nvh12.log_processing.domain.model.FailedLogEntry;
import com.nvh12.log_processing.domain.model.RawLog;
import com.nvh12.log_processing.domain.service.DropAuditRepository;
import com.nvh12.log_processing.infrastructure.config.LogProcessingProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedisDlqRepositoryTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ListOperations<String, String> listOps;
    @Mock
    private DropAuditRepository dropAuditRepository;

    private RedisDlqRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final LogProcessingProperties PROPERTIES = new LogProcessingProperties(
            10, 1, 10000, 40, 10000, 2000, 3, 30000L, 5000L,
            new LogProcessingProperties.ThreadPool(2, 4, 10, 5),
            new LogProcessingProperties.Validation(45, 2048, 512));

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForList()).thenReturn(listOps);
        repository = new RedisDlqRepository(redisTemplate, objectMapper, dropAuditRepository, PROPERTIES, new SimpleMeterRegistry());
    }

    private RawLog makeRawLog(String id) {
        return RawLog.builder().id(id).rawMessage("raw").source("http").receivedAt(Instant.now()).build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void savePersistsEntryWhenBelowCapacity() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(1L);

        repository.save(makeRawLog("id-1"), "parse error");

        verify(redisTemplate).execute(any(RedisScript.class), anyList(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void saveDropsEntryWhenAtCapacity() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(0L);

        repository.save(makeRawLog("id-full"), "overflow");

        // Verify the script was called but entry was not saved (script returned 0)
        verify(redisTemplate).execute(any(RedisScript.class), anyList(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getFailedLogEntriesDeserializesEntries() throws Exception {
        RawLog rawLog = makeRawLog("id-dequeue");
        FailedLogEntry entry = FailedLogEntry.of(rawLog, "parse error");
        String serialized = objectMapper.writeValueAsString(entry);

        when(listOps.leftPop(anyString(), anyLong())).thenReturn(List.of(serialized));

        List<FailedLogEntry> result = repository.getFailedLogEntries(10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).rawLog().getId()).isEqualTo("id-dequeue");
        assertThat(result.get(0).failureReason()).isEqualTo("parse error");
        assertThat(result.get(0).retryCount()).isEqualTo(0);
    }

    @Test
    void getFailedLogEntriesReturnsEmptyListWhenQueueIsEmpty() {
        when(listOps.leftPop(anyString(), anyLong())).thenReturn(List.of());

        assertThat(repository.getFailedLogEntries(10)).isEmpty();
    }

    @Test
    void getFailedLogEntriesReturnsEmptyListWhenRedisReturnsNull() {
        when(listOps.leftPop(anyString(), anyLong())).thenReturn(null);

        assertThat(repository.getFailedLogEntries(10)).isEmpty();
    }
}
