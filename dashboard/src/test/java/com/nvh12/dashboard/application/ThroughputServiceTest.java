package com.nvh12.dashboard.application;

import com.nvh12.dashboard.application.port.BroadcastPort;
import com.nvh12.dashboard.application.port.FlowLogRepository;
import com.nvh12.dashboard.application.port.HttpLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ThroughputServiceTest {

    @Mock HttpLogRepository httpLogRepository;
    @Mock FlowLogRepository flowLogRepository;
    @Mock BroadcastPort broadcastPort;
    @InjectMocks ThroughputService service;

    @Test
    void computeAndBroadcast_broadcastsCorrectRates() {
        when(httpLogRepository.countSince(any(Instant.class))).thenReturn(10L);
        when(flowLogRepository.countSince(any(Instant.class))).thenReturn(4L);

        service.computeAndBroadcast();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(broadcastPort).broadcast(eq("log_throughput"), captor.capture());
        Map<String, Object> payload = captor.getValue();
        assertThat((double) payload.get("http_per_sec")).isEqualTo(5.0);
        assertThat((double) payload.get("flow_per_sec")).isEqualTo(2.0);
        assertThat(payload).containsKey("ts");
    }

    @Test
    void computeAndBroadcast_zeroCounts_broadcastsZeroRates() {
        when(httpLogRepository.countSince(any(Instant.class))).thenReturn(0L);
        when(flowLogRepository.countSince(any(Instant.class))).thenReturn(0L);

        service.computeAndBroadcast();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(broadcastPort).broadcast(eq("log_throughput"), captor.capture());
        assertThat((double) captor.getValue().get("http_per_sec")).isEqualTo(0.0);
        assertThat((double) captor.getValue().get("flow_per_sec")).isEqualTo(0.0);
    }

    @Test
    void computeAndBroadcast_repositoryThrows_doesNotPropagate() {
        when(httpLogRepository.countSince(any(Instant.class))).thenThrow(new RuntimeException("DB down"));

        assertThatNoException().isThrownBy(() -> service.computeAndBroadcast());
        verifyNoInteractions(broadcastPort);
    }

    @Test
    void computeAndBroadcast_queriesWithinTwoSecondWindow() {
        when(httpLogRepository.countSince(any(Instant.class))).thenReturn(6L);
        when(flowLogRepository.countSince(any(Instant.class))).thenReturn(0L);

        Instant before = Instant.now();
        service.computeAndBroadcast();
        Instant after = Instant.now();

        ArgumentCaptor<Instant> sinceCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(httpLogRepository).countSince(sinceCaptor.capture());
        Instant since = sinceCaptor.getValue();
        assertThat(since).isAfter(before.minusSeconds(3)).isBefore(after.minusSeconds(1));
    }
}
