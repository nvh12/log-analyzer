package com.nvh12.log_processing.infrastructure.service;

import com.nvh12.log_processing.domain.model.RawLog;
import com.nvh12.log_processing.domain.service.QueueService;
import com.nvh12.log_processing.infrastructure.config.LogProcessingProperties;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
@Slf4j
public class RedisQueueService implements QueueService {

    static final String QUEUE_KEY = "raw-log-queue";

    private static final RedisScript<Long> ENQUEUE_SCRIPT = RedisScript.of("""
            if tonumber(redis.call('ZCARD', KEYS[1])) >= tonumber(ARGV[1]) then
                return 0
            end
            return redis.call('ZADD', KEYS[1], ARGV[2], ARGV[3])
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final int capacity;

    public RedisQueueService(StringRedisTemplate redisTemplate,
                             ObjectMapper objectMapper,
                             LogProcessingProperties properties,
                             MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.capacity = properties.mainQueueCapacity();

        meterRegistry.gauge("logs.queue.size", this, svc -> {
            Long size = svc.redisTemplate.opsForZSet().zCard(QUEUE_KEY);
            return size != null ? size.doubleValue() : 0.0;
        });
    }

    @Override
    public boolean enqueue(RawLog rawLog) {
        if (rawLog.getReceivedAt() == null) {
            log.warn("Rejecting log id={} — null receivedAt", rawLog.getId());
            return false;
        }

        try {
            double score = rawLog.getReceivedAt().toEpochMilli();
            String value = objectMapper.writeValueAsString(rawLog);

            Long result = redisTemplate.execute(
                    ENQUEUE_SCRIPT,
                    List.of(QUEUE_KEY),
                    String.valueOf(capacity),
                    String.valueOf(score),
                    value);
            return result != null && result.longValue() == 1L;
        } catch (Exception e) {
            throw new RuntimeException("Failed to enqueue log id=" + rawLog.getId(), e);
        }
    }

    @Override
    public List<RawLog> dequeueBatch(int batchSize) {
        Set<ZSetOperations.TypedTuple<String>> values =
                redisTemplate.opsForZSet().popMin(QUEUE_KEY, batchSize);

        if (values == null) {
            return List.of();
        }

        return values.stream()
                .map(ZSetOperations.TypedTuple::getValue)
                .filter(Objects::nonNull)
                .map(v -> {
                    try {
                        return objectMapper.readValue(v, RawLog.class);
                    } catch (Exception e) {
                        log.error("Failed to deserialize queue entry — discarding", e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
    }
}
