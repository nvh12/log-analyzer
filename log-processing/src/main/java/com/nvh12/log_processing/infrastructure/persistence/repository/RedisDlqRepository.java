package com.nvh12.log_processing.infrastructure.persistence.repository;

import com.nvh12.log_processing.domain.model.DropReason;
import com.nvh12.log_processing.domain.model.FailedLogEntry;
import com.nvh12.log_processing.domain.model.RawLog;
import com.nvh12.log_processing.domain.service.DropAuditRepository;
import com.nvh12.log_processing.domain.service.FailedLogRepository;
import com.nvh12.log_processing.infrastructure.config.LogProcessingProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Objects;

@Component
@Slf4j
public class RedisDlqRepository implements FailedLogRepository {

    static final String DLQ_KEY = "failed-log-queue";

    private static final RedisScript<Long> SAVE_SCRIPT = RedisScript.of("""
            if redis.call('LLEN', KEYS[1]) >= tonumber(ARGV[1]) then
                return 0
            end
            redis.call('RPUSH', KEYS[1], ARGV[2])
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final DropAuditRepository dropAuditRepository;
    private final int capacity;
    private final Counter dlqDroppedCounter;
    private final Counter dlqDoubleFaultCounter;

    public RedisDlqRepository(StringRedisTemplate redisTemplate,
                              ObjectMapper objectMapper,
                              DropAuditRepository dropAuditRepository,
                              LogProcessingProperties properties,
                              MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.dropAuditRepository = dropAuditRepository;
        this.capacity = properties.dlqCapacity();
        this.dlqDroppedCounter = meterRegistry.counter("logs.dlq.dropped");
        // Both Redis (DLQ full/unreachable) and Postgres (audit write) failed for the same
        // entry — there's nowhere left to requeue it. This counter is the only signal that
        // such an entry was permanently and silently lost; the full entry is still logged above.
        this.dlqDoubleFaultCounter = meterRegistry.counter("logs.dlq.double_fault");

        meterRegistry.gauge("logs.dlq.size", this, repo -> {
            Long size = repo.redisTemplate.opsForList().size(DLQ_KEY);
            return size != null ? size.doubleValue() : 0.0;
        });
    }

    @Override
    public void save(RawLog rawLog, String reason) {
        saveEntry(FailedLogEntry.of(rawLog, reason));
    }

    @Override
    public void saveEntry(FailedLogEntry entry) {
        try {
            String json = objectMapper.writeValueAsString(entry);
            Long result = redisTemplate.execute(
                    SAVE_SCRIPT,
                    List.of(DLQ_KEY),
                    String.valueOf(capacity),
                    json);

            if (!Long.valueOf(1).equals(result)) {
                log.error("DLQ full — dropping log. id={}, reason={}",
                        entry.rawLog().getId(), entry.failureReason());
                dlqDroppedCounter.increment();
                try {
                    dropAuditRepository.record(entry, DropReason.DLQ_OVERFLOW);
                } catch (Exception ae) {
                    log.error("Failed to persist DLQ overflow to audit store — entry is permanently lost (DLQ full, audit write failed). id={}",
                            entry.rawLog().getId(), ae);
                    dlqDoubleFaultCounter.increment();
                }
            }
        } catch (Exception e) {
            log.error("Failed to persist to DLQ. id={}", entry.rawLog().getId(), e);
            try {
                dropAuditRepository.record(entry, DropReason.DLQ_SAVE_FAILED);
            } catch (Exception ae) {
                log.error("Failed to persist DLQ save failure to audit store — entry is permanently lost (Redis unreachable, audit write failed). id={}",
                        entry.rawLog().getId(), ae);
                dlqDoubleFaultCounter.increment();
            }
        }
    }

    @Override
    public List<FailedLogEntry> getFailedLogEntries(int batchSize) {
        List<String> values = redisTemplate.opsForList().leftPop(DLQ_KEY, batchSize);
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .map(v -> {
                    try {
                        return objectMapper.readValue(v, FailedLogEntry.class);
                    } catch (Exception e) {
                        log.error("Failed to deserialize DLQ entry — auditing and discarding", e);
                        try {
                            dropAuditRepository.recordDeadLetter(v, null,
                                    "DLQ deserialization failure: " + e.getMessage());
                        } catch (Exception ae) {
                            log.error("Failed to persist corrupt DLQ entry to audit store", ae);
                        }
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
    }
}
