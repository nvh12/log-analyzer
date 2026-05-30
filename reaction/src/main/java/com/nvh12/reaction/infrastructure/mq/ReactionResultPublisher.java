package com.nvh12.reaction.infrastructure.mq;

import com.nvh12.reaction.config.RabbitMqConfig;
import com.nvh12.reaction.service.dto.ReactionAction;
import com.nvh12.reaction.service.dto.Severity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReactionResultPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publish(Long reactionId, ReactionAction action, String target, Severity severity, Instant ts) {
        try {
            Map<String, Object> message = Map.of(
                    "reaction_id", reactionId,
                    "action", action.name(),
                    "target", target != null ? target : "",
                    "ttl_seconds", ttlSeconds(action, severity),
                    "ts", ts.toString()
            );
            rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE_REACTION_RESULTS, "", message);
        } catch (Exception e) {
            log.error("Failed to publish reaction result [id={} action={}]: {}", reactionId, action, e.getMessage());
        }
    }

    private long ttlSeconds(ReactionAction action, Severity severity) {
        if (action == ReactionAction.SCALE_UP) return 0L;
        return switch (severity) {
            case NONE -> throw new IllegalStateException("NONE severity should not reach reaction publisher");
            case LOW -> 5 * 60L;
            case MEDIUM -> 30 * 60L;
            case HIGH -> 2 * 3600L;
            case CRITICAL -> 24 * 3600L;
        };
    }
}
