package com.nvh12.reaction.service.impl;

import com.nvh12.reaction.service.AlertChannel;
import com.nvh12.reaction.service.AlertService;
import com.nvh12.reaction.service.dto.DetectionType;
import com.nvh12.reaction.service.dto.alert.Alert;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Slf4j
@Primary
@Service
public class CompositeAlertService implements AlertService {

    private final List<AlertChannel> channels;
    private final Map<DetectionType, Queue<Alert>> queues;

    public CompositeAlertService(List<AlertChannel> channels) {
        this.channels = channels;
        this.queues = new ConcurrentHashMap<>();
        for (DetectionType type : DetectionType.values()) {
            queues.put(type, new ConcurrentLinkedQueue<>());
        }
    }

    @PostConstruct
    void validateChannels() {
        if (channels.isEmpty()) {
            log.warn("No AlertChannel beans are active — all alerts will be silently dropped. "
                    + "Check alert.provider and alert.discord.webhook-url configuration.");
        }
    }

    @Override
    public void enqueue(Alert alert) {
        queues.get(alert.getDetectionType()).add(alert);
        log.debug("Queued {} alert [severity={} ip={}]",
                alert.getDetectionType(), alert.getSeverity(), alert.getSourceIp());
    }

    @Scheduled(
            fixedDelayString = "${alert.batch-interval-seconds:180}000",
            initialDelayString = "${alert.batch-interval-seconds:180}000"
    )
    public void flush() {
        queues.forEach((type, queue) -> {
            List<Alert> batch = drain(queue);
            if (batch.isEmpty()) return;
            log.debug("Flushing {} queued {} alert(s) to {} channel(s)", batch.size(), type, channels.size());
            for (AlertChannel channel : channels) {
                try {
                    channel.alertBatch(batch);
                } catch (Exception e) {
                    log.error("Alert channel {} failed for {} batch of {} [{}]: {}",
                            channel.getClass().getSimpleName(), type, batch.size(),
                            batchSummary(batch), e.getMessage());
                }
            }
        });
    }

    private static List<Alert> drain(Queue<Alert> queue) {
        List<Alert> batch = new ArrayList<>();
        Alert a;
        while ((a = queue.poll()) != null) batch.add(a);
        return batch;
    }

    private static String batchSummary(List<Alert> batch) {
        if (batch.isEmpty()) return "empty";
        return "severity range: " + batch.stream()
                .map(a -> a.getSeverity().name())
                .distinct()
                .sorted()
                .reduce((a, b) -> a + "/" + b)
                .orElse("?");
    }
}
