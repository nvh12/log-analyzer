package com.nvh12.log_processing.presentation;

import com.nvh12.log_processing.domain.model.RawLog;
import com.nvh12.log_processing.domain.service.DropAuditRepository;
import com.nvh12.log_processing.domain.service.FailedLogRepository;
import com.nvh12.log_processing.infrastructure.config.RabbitMqConfig;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class DeadLetterConsumer {

    private final DropAuditRepository dropAuditRepository;
    private final FailedLogRepository failedLogRepository;
    private final ObjectMapper objectMapper;
    private final Counter requeuedCounter;
    private final Counter unrecoverableCounter;

    public DeadLetterConsumer(DropAuditRepository dropAuditRepository,
                               FailedLogRepository failedLogRepository,
                               ObjectMapper objectMapper,
                               MeterRegistry meterRegistry) {
        this.dropAuditRepository = dropAuditRepository;
        this.failedLogRepository = failedLogRepository;
        this.objectMapper = objectMapper;
        this.requeuedCounter = meterRegistry.counter("logs.dlx.requeued_to_dlq");
        this.unrecoverableCounter = meterRegistry.counter("logs.dlx.unrecoverable");
    }

    @RabbitListener(queues = RabbitMqConfig.QUEUE_RAW_DLQ, containerFactory = "dlqContainerFactory")
    public void onDeadLetter(Message message) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        String logId = extractLogId(body);
        String reason = extractDeathReason(message);

        try {
            dropAuditRepository.recordDeadLetter(body, logId, reason);
        } catch (Exception e) {
            // Audit write failed. Unlike RedisDlqRepository's case, this message came from
            // RabbitMQ's DLX (not the Redis DLQ), so requeueing to Redis is a legitimate
            // cross-pipeline action, not circular. Only possible if the body still
            // deserializes into a RawLog — if it doesn't, there's nowhere safe to put it.
            log.error("Failed to persist dead-letter to audit store. logId={}, reason={}, body={}",
                    logId, reason, body, e);
            try {
                RawLog rawLog = objectMapper.readValue(body, RawLog.class);
                failedLogRepository.save(rawLog, reason);
                requeuedCounter.increment();
                log.warn("Requeued dead-letter to Redis DLQ after audit write failure. logId={}", logId);
            } catch (Exception parseEx) {
                unrecoverableCounter.increment();
                log.error("Dead-letter body could not be requeued (not a valid RawLog) — entry is permanently lost. logId={}",
                        logId, parseEx);
            }
        }
    }

    private String extractLogId(String body) {
        try {
            String text = objectMapper.readTree(body).path("id").asText();
            return text.isEmpty() ? null : text;
        } catch (Exception e) {
            return null;
        }
    }

    private String extractDeathReason(Message message) {
        List<Map<String, ?>> xDeath = message.getMessageProperties().getXDeathHeader();
        if (xDeath == null || xDeath.isEmpty()) return "unknown";
        Object reason = xDeath.getFirst().get("reason");
        return reason != null ? reason.toString() : "unknown";
    }
}
