package com.nvh12.log_processing.presentation;

import com.nvh12.log_processing.domain.service.DropAuditRepository;
import com.nvh12.log_processing.infrastructure.config.RabbitMqConfig;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Component
@AllArgsConstructor
@Slf4j
public class DeadLetterConsumer {

    private final DropAuditRepository dropAuditRepository;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitMqConfig.QUEUE_RAW_DLQ)
    public void onDeadLetter(Message message) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        String logId = extractLogId(body);
        String reason = extractDeathReason(message);

        try {
            dropAuditRepository.recordDeadLetter(body, logId, reason);
        } catch (Exception e) {
            // Log full body so the message can be recovered from logs if persistence fails.
            log.error("Failed to persist dead-letter to audit store. logId={}, reason={}, body={}",
                    logId, reason, body, e);
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
