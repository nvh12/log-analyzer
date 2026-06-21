package com.nvh12.reaction.presentation;

import com.nvh12.reaction.infrastructure.config.RabbitMqConfig;
import com.nvh12.reaction.service.DroppedReactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReactionDeadLetterConsumer {

    private final DroppedReactionService droppedReactionService;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitMqConfig.QUEUE_DETECTION_RESULTS_DLQ,
            containerFactory = "reactionDlqContainerFactory")
    public void onDeadLetter(Message message) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        String reason = extractDeathReason(message);

        String detectionType = null;
        String sourceIp = null;
        String severity = null;
        Instant detectedAt = null;
        try {
            JsonNode node = objectMapper.readTree(body);
            detectionType = textOrNull(node, "detection_type");
            sourceIp = textOrNull(node, "source_ip");
            severity = textOrNull(node, "severity");
            String detectedAtText = textOrNull(node, "detected_at");
            detectedAt = detectedAtText != null ? Instant.parse(detectedAtText) : null;
        } catch (Exception e) {
            log.warn("Dead-lettered reaction body could not be parsed for audit fields; storing raw payload only", e);
        }

        try {
            droppedReactionService.record(detectionType, sourceIp, severity, detectedAt, reason, body);
        } catch (Exception e) {
            // Nowhere further to route a dead-lettered reaction that also fails to audit —
            // log the full entry so it isn't silently lost, same as the leaf case in
            // log-processing's DeadLetterConsumer.
            log.error("Failed to persist dropped-reaction audit row. reason={}, body={}", reason, body, e);
        }
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return (value == null || value.isNull()) ? null : value.asText();
    }

    private String extractDeathReason(Message message) {
        List<Map<String, ?>> xDeath = message.getMessageProperties().getXDeathHeader();
        if (xDeath == null || xDeath.isEmpty()) return "unknown";
        Object reason = xDeath.getFirst().get("reason");
        return reason != null ? reason.toString() : "unknown";
    }
}
