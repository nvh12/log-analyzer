package com.nvh12.reaction.service.impl;

import com.nvh12.reaction.config.AlertProperties;
import com.nvh12.reaction.service.dto.DetectionType;
import com.nvh12.reaction.service.dto.Severity;
import com.nvh12.reaction.service.dto.alert.Alert;
import com.nvh12.reaction.service.dto.alert.DDoSAlert;
import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.Emails;
import com.resend.services.emails.model.CreateEmailOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResendAlertServiceTest {

    @Mock Resend resend;
    @Mock Emails emails;
    @Mock AlertProperties alertProperties;
    @Mock AlertProperties.Mail mail;

    ResendAlertService service;

    @BeforeEach
    void setUp() throws ResendException {
        lenient().when(resend.emails()).thenReturn(emails);
        lenient().when(alertProperties.getMail()).thenReturn(mail);
        lenient().when(mail.getFrom()).thenReturn("alerts@example.com");
        lenient().when(mail.getTo()).thenReturn("admin@example.com");
        service = new ResendAlertService(resend, alertProperties);
    }

    private CreateEmailOptions captureOptions() throws ResendException {
        ArgumentCaptor<CreateEmailOptions> captor = ArgumentCaptor.forClass(CreateEmailOptions.class);
        verify(emails).send(captor.capture());
        return captor.getValue();
    }

    @Test
    void alert_setsFromToAndSubject() throws ResendException {
        DDoSAlert alert = DDoSAlert.builder()
                .detectionType(DetectionType.DDOS)
                .sourceIp("1.2.3.4")
                .severity(Severity.CRITICAL)
                .detectedAt(Instant.now())
                .build();

        service.alert(alert);

        CreateEmailOptions options = captureOptions();
        assertThat(options.getFrom()).isEqualTo("alerts@example.com");
        assertThat(options.getTo()).containsExactly("admin@example.com");
        assertThat(options.getSubject()).isEqualTo("[CRITICAL] DDOS detected from 1.2.3.4");
    }

    @Test
    void alert_forDDoSAlert_includesDestinationInBody() throws ResendException {
        DDoSAlert alert = DDoSAlert.builder()
                .detectionType(DetectionType.DDOS)
                .sourceIp("1.2.3.4")
                .severity(Severity.HIGH)
                .detectedAt(Instant.now())
                .destIp("10.0.0.1")
                .destPort(80)
                .build();

        service.alert(alert);

        assertThat(captureOptions().getHtml()).contains("10.0.0.1:80");
    }

    @Test
    void alertBatch_sendsOneEmailWithBatchSubjectAndBody() throws ResendException {
        List<Alert> alerts = List.of(
                DDoSAlert.builder()
                        .detectionType(DetectionType.DDOS).sourceIp("1.1.1.1")
                        .severity(Severity.HIGH).detectedAt(Instant.now()).build(),
                DDoSAlert.builder()
                        .detectionType(DetectionType.DDOS).sourceIp("2.2.2.2")
                        .severity(Severity.CRITICAL).detectedAt(Instant.now()).build()
        );

        service.alertBatch(alerts);

        CreateEmailOptions options = captureOptions();
        assertThat(options.getFrom()).isEqualTo("alerts@example.com");
        assertThat(options.getTo()).containsExactly("admin@example.com");
        assertThat(options.getSubject()).isEqualTo("[CRITICAL] 2 DDOS alerts detected");
        assertThat(options.getHtml()).contains("1.1.1.1").contains("2.2.2.2");
    }

    @Test
    void alertBatch_emptyList_sendsNothing() throws ResendException {
        service.alertBatch(List.of());
        verify(emails, never()).send(any());
    }

    @Test
    void alertBatch_whenResendThrows_retriesAndDoesNotRethrow() throws ResendException {
        when(emails.send(any())).thenThrow(new ResendException("network error"));

        List<Alert> alerts = List.of(
                DDoSAlert.builder()
                        .detectionType(DetectionType.DDOS).sourceIp("1.1.1.1")
                        .severity(Severity.LOW).detectedAt(Instant.now()).build()
        );

        assertDoesNotThrow(() -> service.alertBatch(alerts));
        verify(emails, times(3)).send(any());
    }

    @Test
    void alert_whenResendThrows_retriesAndDoesNotRethrow() throws ResendException {
        when(emails.send(any())).thenThrow(new ResendException("network error"));

        DDoSAlert alert = DDoSAlert.builder()
                .detectionType(DetectionType.DDOS)
                .sourceIp("1.1.1.1")
                .severity(Severity.LOW)
                .detectedAt(Instant.now())
                .build();

        assertDoesNotThrow(() -> service.alert(alert));
        verify(emails, times(3)).send(any());
    }

    @Test
    void alert_succeedsAfterTransientFailure() throws ResendException {
        when(emails.send(any()))
                .thenThrow(new ResendException("timeout"))
                .thenReturn(null);

        DDoSAlert alert = DDoSAlert.builder()
                .detectionType(DetectionType.DDOS)
                .sourceIp("1.1.1.1")
                .severity(Severity.LOW)
                .detectedAt(Instant.now())
                .build();

        assertDoesNotThrow(() -> service.alert(alert));
        verify(emails, times(2)).send(any());
    }
}
