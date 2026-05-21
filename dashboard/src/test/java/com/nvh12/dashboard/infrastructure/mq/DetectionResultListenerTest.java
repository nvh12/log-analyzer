package com.nvh12.dashboard.infrastructure.mq;

import com.nvh12.dashboard.domain.DetectionType;
import com.nvh12.dashboard.domain.NetworkLayer;
import com.nvh12.dashboard.domain.Severity;
import com.nvh12.dashboard.infrastructure.mq.dto.DetectionMessage;
import com.nvh12.dashboard.infrastructure.sse.SseEmitterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DetectionResultListenerTest {

    @Mock SseEmitterRegistry registry;
    @InjectMocks DetectionResultListener listener;

    @Test
    void onDetection_nullMessage_doesNotBroadcast() {
        listener.onDetection(null);
        verifyNoInteractions(registry);
    }

    @Test
    void onDetection_nullDetectionType_doesNotBroadcast() {
        DetectionMessage msg = new DetectionMessage(
                null, NetworkLayer.HTTP, Severity.HIGH, true, 0.9, "1.2.3.4",
                null, null, null, null, null, Instant.now(), null, null);
        listener.onDetection(msg);
        verifyNoInteractions(registry);
    }

    @Test
    void onDetection_validMessage_broadcastsDetectionEvent() {
        Instant now = Instant.now();
        DetectionMessage msg = new DetectionMessage(
                DetectionType.DDOS, NetworkLayer.HTTP, Severity.HIGH, true, 0.95, "1.2.3.4",
                "10.0.0.1", 443, null, null, null, now, null, null);

        listener.onDetection(msg);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(registry).broadcast(eq("detection"), captor.capture());
        Map<String, Object> summary = captor.getValue();
        assertThat(summary).containsEntry("detection_type", "DDOS")
                .containsEntry("severity", "HIGH")
                .containsEntry("anomaly", true)
                .containsEntry("confidence", 0.95)
                .containsEntry("source_ip", "1.2.3.4")
                .containsEntry("ts", now.toString());
    }

    @Test
    void onDetection_nullDetectedAt_setsNullTs() {
        DetectionMessage msg = new DetectionMessage(
                DetectionType.WEB_ATTACK, NetworkLayer.HTTP, Severity.MEDIUM, false, 0.7, "5.5.5.5",
                null, null, null, null, null, null, null, null);

        listener.onDetection(msg);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(registry).broadcast(eq("detection"), captor.capture());
        assertThat(captor.getValue()).containsEntry("ts", null);
    }

    @Test
    void onDetection_multipleValidMessages_broadcastsEach() {
        DetectionMessage msg1 = new DetectionMessage(DetectionType.BRUTE_FORCE, NetworkLayer.HTTP, Severity.LOW, true, 0.6, "1.1.1.1",
                null, null, null, null, null, Instant.now(), null, null);
        DetectionMessage msg2 = new DetectionMessage(DetectionType.TRAFFIC, NetworkLayer.FLOW, Severity.CRITICAL, true, 0.99, "2.2.2.2",
                null, null, null, null, null, Instant.now(), null, null);

        listener.onDetection(msg1);
        listener.onDetection(msg2);

        verify(registry, times(2)).broadcast(eq("detection"), any());
    }
}
