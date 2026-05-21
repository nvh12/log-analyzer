package com.nvh12.dashboard.infrastructure.mq;

import com.nvh12.dashboard.AbstractContainerIT;
import com.nvh12.dashboard.config.RabbitMqConfig;
import com.nvh12.dashboard.domain.ReactionAction;
import com.nvh12.dashboard.infrastructure.mq.dto.ReactionResultMessage;
import com.nvh12.dashboard.infrastructure.sse.SseEmitterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.Map;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

class MqListenerIT extends AbstractContainerIT {

    @MockitoBean
    SseEmitterRegistry registry;

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Test
    void detectionResultListener_publishToFanout_broadcastsDetectionEvent() {
        Map<String, Object> payload = Map.of(
                "detection_type", "DDOS",
                "network_layer", "HTTP",
                "severity", "HIGH",
                "anomaly", true,
                "confidence", 0.95,
                "source_ip", "1.2.3.4");

        rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE_DETECTION_RESULTS, "", payload);

        await().atMost(5, SECONDS).untilAsserted(() -> {
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
            verify(registry).broadcast(eq("detection"), captor.capture());
            Map<String, Object> summary = captor.getValue();
            assertThat(summary).containsEntry("detection_type", "DDOS")
                    .containsEntry("severity", "HIGH")
                    .containsEntry("source_ip", "1.2.3.4");
        });
    }

    @Test
    void reactionResultListener_publishToFanout_broadcastsReactionEvent() {
        Map<String, Object> payload = Map.of(
                "reaction_id", 42,
                "action", "BLOCK",
                "target", "9.9.9.9",
                "ttl_seconds", 1800,
                "ts", Instant.now().toString());

        rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE_REACTION_RESULTS, "", payload);

        await().atMost(5, SECONDS).untilAsserted(() ->
                verify(registry).broadcast(eq("reaction"), any(ReactionResultMessage.class))
        );
    }

    @Test
    void reactionResultListener_scaleUpAction_broadcastsWithEmptyTarget() {
        Map<String, Object> payload = Map.of(
                "reaction_id", 99,
                "action", "SCALE_UP",
                "target", "",
                "ttl_seconds", 0,
                "ts", Instant.now().toString());

        rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE_REACTION_RESULTS, "", payload);

        await().atMost(5, SECONDS).untilAsserted(() -> {
            ArgumentCaptor<ReactionResultMessage> captor = ArgumentCaptor.forClass(ReactionResultMessage.class);
            verify(registry).broadcast(eq("reaction"), captor.capture());
            assertThat(captor.getValue().action()).isEqualTo(ReactionAction.SCALE_UP);
            assertThat(captor.getValue().ttlSeconds()).isEqualTo(0L);
        });
    }
}
