package com.nvh12.log_processing.infrastructure.service;

import com.nvh12.log_processing.domain.model.LogSource;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedisQueueServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @SuppressWarnings("unchecked")
    @Mock
    private ZSetOperations<String, String> zSetOps;

    @Mock
    private DropAuditRepository dropAuditRepository;

    private RedisQueueService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final LogProcessingProperties PROPERTIES = new LogProcessingProperties(
            10, 1, 10000, 5, 10000, 2000, 3, 50, 30000L, 5000L,
            new LogProcessingProperties.ThreadPool(2, 4, 10, 5),
            new LogProcessingProperties.Validation(45, 2048, 512));

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        service = new RedisQueueService(redisTemplate, objectMapper, dropAuditRepository, PROPERTIES, new SimpleMeterRegistry());
    }

    private RawLog makeRawLog(String id) {
        return RawLog.builder()
                .id(id)
                .rawMessage("GET /index.html HTTP/1.0")
                .source(LogSource.HTTP)
                .receivedAt(Instant.parse("2024-01-01T00:00:00Z"))
                .build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void enqueueAddsToZSetAndReturnsTrue() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any()))
                .thenReturn(1L);

        boolean result = service.enqueue(makeRawLog("id-1"));

        assertThat(result).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void enqueueReturnsFalseWhenQueueAtCapacity() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any()))
                .thenReturn(0L);

        boolean result = service.enqueue(makeRawLog("id-overflow"));

        assertThat(result).isFalse();
    }

    @Test
    void enqueueReturnsFalseWhenReceivedAtIsNull() {
        RawLog log = RawLog.builder().id("id-null").rawMessage("x").source(LogSource.HTTP).build();

        boolean result = service.enqueue(log);

        assertThat(result).isFalse();
        verifyNoInteractions(zSetOps);
    }

    @Test
    @SuppressWarnings("unchecked")
    void dequeueBatchDeserializesAndReturnsLogs() throws Exception {
        RawLog original = makeRawLog("id-dequeue");
        String serialized = objectMapper.writeValueAsString(original);

        ZSetOperations.TypedTuple<String> tuple = mock(ZSetOperations.TypedTuple.class);
        when(tuple.getValue()).thenReturn(serialized);
        when(zSetOps.popMin(anyString(), anyLong())).thenReturn(Set.of(tuple));

        List<RawLog> result = service.dequeueBatch(10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("id-dequeue");
        assertThat(result.get(0).getSource()).isEqualTo(LogSource.HTTP);
    }

    @Test
    void dequeueBatchReturnsEmptyListWhenZSetReturnsNull() {
        when(zSetOps.popMin(anyString(), anyLong())).thenReturn(null);

        assertThat(service.dequeueBatch(10)).isEmpty();
    }

    @Test
    void dequeueBatchReturnsEmptyListForEmptySet() {
        when(zSetOps.popMin(anyString(), anyLong())).thenReturn(Set.of());

        assertThat(service.dequeueBatch(10)).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void dequeueBatchAuditsCorruptEntry() {
        ZSetOperations.TypedTuple<String> tuple = mock(ZSetOperations.TypedTuple.class);
        when(tuple.getValue()).thenReturn("not-valid-json");
        when(zSetOps.popMin(anyString(), anyLong())).thenReturn(Set.of(tuple));

        List<RawLog> result = service.dequeueBatch(10);

        assertThat(result).isEmpty();
        verify(dropAuditRepository).recordDeadLetter(eq("not-valid-json"), isNull(),
                contains("Queue deserialization failure"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void dequeueBatchHandlesLowercaseLogSource() throws Exception {
        String json = "{\"id\":\"id-lc\",\"rawMessage\":\"x\",\"source\":\"http\",\"receivedAt\":\"2024-01-01T00:00:00Z\"}";
        ZSetOperations.TypedTuple<String> tuple = mock(ZSetOperations.TypedTuple.class);
        when(tuple.getValue()).thenReturn(json);
        when(zSetOps.popMin(anyString(), anyLong())).thenReturn(Set.of(tuple));

        List<RawLog> result = service.dequeueBatch(10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSource()).isEqualTo(LogSource.HTTP);
    }
}
