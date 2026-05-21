package com.nvh12.log_processing.presentation;

import com.nvh12.log_processing.domain.model.RawLog;
import com.nvh12.log_processing.domain.service.QueueService;
import com.nvh12.log_processing.infrastructure.config.RabbitMqConfig;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
@Slf4j
public class RawLogConsumer {

    private final QueueService queueService;

    @RabbitListener(queues = RabbitMqConfig.QUEUE_RAW)
    public void onMessage(RawLog rawLog) {
        if (rawLog.getReceivedAt() == null) {
            log.warn("Rejecting log id={} — null receivedAt, acking silently", rawLog.getId());
            return;
        }
        boolean accepted;
        try {
            accepted = queueService.enqueue(rawLog);
        } catch (Exception e) {
            log.error("Failed to enqueue log — dead-lettering id={}: {}", rawLog.getId(), e.getMessage());
            throw new AmqpRejectAndDontRequeueException("Enqueue failed: " + e.getMessage(), e);
        }

        if (!accepted) {
            log.warn("Queue full — dead-lettering message id={}", rawLog.getId());
            throw new AmqpRejectAndDontRequeueException("Queue full");
        }
    }
}
