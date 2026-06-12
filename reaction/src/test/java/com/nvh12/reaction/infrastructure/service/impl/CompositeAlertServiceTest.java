package com.nvh12.reaction.infrastructure.service.impl;

import com.nvh12.reaction.service.AlertChannel;
import com.nvh12.reaction.service.dto.DetectionType;
import com.nvh12.reaction.service.dto.Severity;
import com.nvh12.reaction.service.dto.alert.Alert;
import com.nvh12.reaction.service.dto.alert.DDoSAlert;
import com.nvh12.reaction.service.dto.alert.TrafficAlert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CompositeAlertServiceTest {

    @Mock AlertChannel channelA;
    @Mock AlertChannel channelB;

    private Alert ddosAlert(String ip, Severity severity) {
        return DDoSAlert.builder()
                .detectionType(DetectionType.DDOS).sourceIp(ip)
                .severity(severity).detectedAt(Instant.now()).build();
    }

    private Alert trafficAlert() {
        return TrafficAlert.builder()
                .detectionType(DetectionType.TRAFFIC).sourceIp("9.9.9.9")
                .severity(Severity.LOW).detectedAt(Instant.now()).build();
    }

    @Test
    void flush_withQueuedAlerts_dispatchesBatchToEachChannel() {
        CompositeAlertService service = new CompositeAlertService(List.of(channelA, channelB));
        Alert alert1 = ddosAlert("1.1.1.1", Severity.HIGH);
        Alert alert2 = ddosAlert("2.2.2.2", Severity.CRITICAL);

        service.enqueue(alert1);
        service.enqueue(alert2);
        service.flush();

        verify(channelA).alertBatch(List.of(alert1, alert2));
        verify(channelB).alertBatch(List.of(alert1, alert2));
    }

    @Test
    void flush_separatesQueuesByDetectionType() {
        CompositeAlertService service = new CompositeAlertService(List.of(channelA));
        Alert ddos = ddosAlert("1.1.1.1", Severity.HIGH);
        Alert traffic = trafficAlert();

        service.enqueue(ddos);
        service.enqueue(traffic);
        service.flush();

        verify(channelA).alertBatch(List.of(ddos));
        verify(channelA).alertBatch(List.of(traffic));
    }

    @Test
    void flush_withNoQueuedAlerts_doesNotCallAnyChannel() {
        CompositeAlertService service = new CompositeAlertService(List.of(channelA));

        service.flush();

        verify(channelA, never()).alertBatch(anyList());
    }

    @Test
    void flush_drainsQueue_secondFlushSendsNothing() {
        CompositeAlertService service = new CompositeAlertService(List.of(channelA));
        service.enqueue(ddosAlert("1.1.1.1", Severity.HIGH));

        service.flush();
        service.flush();

        verify(channelA).alertBatch(anyList());
    }

    @Test
    void flush_oneChannelThrows_otherChannelStillReceivesBatch() {
        CompositeAlertService service = new CompositeAlertService(List.of(channelA, channelB));
        doThrow(new RuntimeException("channel A down")).when(channelA).alertBatch(anyList());
        Alert alert = ddosAlert("1.1.1.1", Severity.HIGH);

        service.enqueue(alert);

        assertThatNoException().isThrownBy(service::flush);
        verify(channelB).alertBatch(List.of(alert));
    }

    @Test
    void validateChannels_emptyChannelList_doesNotThrow() {
        CompositeAlertService service = new CompositeAlertService(List.of());

        assertThatNoException().isThrownBy(service::validateChannels);
    }

    @Test
    void enqueue_doesNotImmediatelyDispatch() {
        CompositeAlertService service = new CompositeAlertService(List.of(channelA));

        service.enqueue(ddosAlert("1.1.1.1", Severity.HIGH));

        verify(channelA, never()).alertBatch(anyList());
    }

}
