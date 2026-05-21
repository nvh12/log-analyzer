package com.nvh12.reaction.presentation;

import com.nvh12.reaction.config.RabbitMqConfig;
import com.nvh12.reaction.service.ReactionService;
import com.nvh12.reaction.service.dto.DetectionType;
import com.nvh12.reaction.service.dto.ReactionInput;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class DetectionResultConsumer {

    private final Map<DetectionType, ReactionService> services;

    public DetectionResultConsumer(List<ReactionService> serviceList) {
        this.services = serviceList.stream()
                .collect(Collectors.toMap(ReactionService::getType, s -> s));
    }

    @RabbitListener(queues = RabbitMqConfig.QUEUE_DETECTION_RESULTS)
    public void consume(ReactionInput input) {
        DetectionType type = input.getDetectionType();
        String sourceIp = input.getSourceIp();
        boolean needsSourceIp = type != null && type != DetectionType.TRAFFIC;
        if (type == null || input.getSeverity() == null || input.getDetectedAt() == null
                || (needsSourceIp && (sourceIp == null || sourceIp.isBlank()))) {
            log.warn("Dropping malformed detection event: type={}, sourceIp={}, severity={}, detectedAt={}",
                    type, sourceIp, input.getSeverity(), input.getDetectedAt());
            return;
        }
        ReactionService service = services.get(type);
        if (service == null) {
            log.warn("No handler registered for detection type: {}", type);
            return;
        }
        log.debug("Dispatching {} detection from {} to {}", type, sourceIp, service.getClass().getSimpleName());
        try {
            service.handle(input);
        } catch (Exception e) {
            log.error("Failed to handle {} detection from {}: {}", type, sourceIp, e.getMessage(), e);
        }
    }
}
