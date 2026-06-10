package com.nvh12.reaction.presentation;

import com.nvh12.reaction.service.ReactionService;
import com.nvh12.reaction.service.dto.DDoSInput;
import com.nvh12.reaction.service.dto.DetectionType;
import com.nvh12.reaction.service.dto.Severity;
import com.nvh12.reaction.service.dto.TrafficInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DetectionResultConsumerTest {

    @Mock ReactionService ddosService;
    @Mock ReactionService trafficService;

    DetectionResultConsumer consumer;

    @BeforeEach
    void setUp() {
        when(ddosService.getType()).thenReturn(DetectionType.DDOS);
        when(trafficService.getType()).thenReturn(DetectionType.TRAFFIC);
        consumer = new DetectionResultConsumer(List.of(ddosService, trafficService));
    }

    private DDoSInput ddosInput(String ip, Severity severity) {
        DDoSInput input = new DDoSInput();
        input.setDetectionType(DetectionType.DDOS);
        input.setSourceIp(ip);
        input.setSeverity(severity);
        input.setDetectedAt(Instant.now());
        return input;
    }

    @Test
    void consume_validInput_dispatchesToMatchingService() {
        DDoSInput input = ddosInput("1.2.3.4", Severity.HIGH);

        consumer.consume(input);

        verify(ddosService).handle(input);
        verify(trafficService, never()).handle(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void consume_nullDetectionType_dropsWithoutDispatch() {
        DDoSInput input = ddosInput("1.2.3.4", Severity.HIGH);
        input.setDetectionType(null);

        consumer.consume(input);

        verify(ddosService, never()).handle(org.mockito.ArgumentMatchers.any());
        verify(trafficService, never()).handle(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void consume_nullSeverity_dropsWithoutDispatch() {
        DDoSInput input = ddosInput("1.2.3.4", null);

        consumer.consume(input);

        verify(ddosService, never()).handle(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void consume_nullDetectedAt_dropsWithoutDispatch() {
        DDoSInput input = ddosInput("1.2.3.4", Severity.HIGH);
        input.setDetectedAt(null);

        consumer.consume(input);

        verify(ddosService, never()).handle(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void consume_blankSourceIpForNonTrafficType_dropsWithoutDispatch() {
        DDoSInput input = ddosInput("", Severity.HIGH);

        consumer.consume(input);

        verify(ddosService, never()).handle(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void consume_trafficTypeWithoutSourceIp_stillDispatches() {
        TrafficInput input = new TrafficInput();
        input.setDetectionType(DetectionType.TRAFFIC);
        input.setSourceIp(null);
        input.setSeverity(Severity.HIGH);
        input.setDetectedAt(Instant.now());

        consumer.consume(input);

        verify(trafficService).handle(input);
    }

    @Test
    void consume_noneSeverity_dropsWithoutDispatch() {
        DDoSInput input = ddosInput("1.2.3.4", Severity.NONE);

        consumer.consume(input);

        verify(ddosService, never()).handle(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void consume_noHandlerRegisteredForType_dropsWithoutThrowing() {
        DetectionResultConsumer ddosOnly = new DetectionResultConsumer(List.of(ddosService));
        TrafficInput input = new TrafficInput();
        input.setDetectionType(DetectionType.TRAFFIC);
        input.setSeverity(Severity.HIGH);
        input.setDetectedAt(Instant.now());

        assertThatNoException().isThrownBy(() -> ddosOnly.consume(input));
    }

    @Test
    void consume_serviceThrows_doesNotPropagate() {
        DDoSInput input = ddosInput("1.2.3.4", Severity.HIGH);
        org.mockito.Mockito.doThrow(new RuntimeException("boom")).when(ddosService).handle(input);

        assertThatNoException().isThrownBy(() -> consumer.consume(input));
    }
}
