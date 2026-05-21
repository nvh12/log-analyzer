package com.nvh12.dashboard.infrastructure.mq;

import com.nvh12.dashboard.infrastructure.mq.dto.DetectionMessage;
import com.nvh12.dashboard.infrastructure.sse.SseEmitterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class DetectionResultListener {

    private final SseEmitterRegistry registry;

    @RabbitListener(queues = "#{@detectionDashboardQueue.name}")
    public void onDetection(DetectionMessage msg) {
        if (msg == null || msg.detectionType() == null) {
            log.warn("Received null or malformed detection message");
            return;
        }
        log.debug("Detection received via MQ: type={} severity={} ip={}", msg.detectionType(), msg.severity(), msg.sourceIp());
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("detection_type", msg.detectionType().name());
        summary.put("severity", msg.severity() != null ? msg.severity().name() : null);
        summary.put("anomaly", msg.anomaly());
        summary.put("confidence", msg.confidence());
        summary.put("source_ip", msg.sourceIp());
        summary.put("ts", msg.detectedAt() != null ? msg.detectedAt().toString() : null);
        registry.broadcast("detection", summary);
    }
}
