package com.nvh12.reaction.service.impl;

import com.nvh12.reaction.config.AlertProperties;
import com.nvh12.reaction.service.dto.DetectionType;
import com.nvh12.reaction.service.dto.MethodFlags;
import com.nvh12.reaction.service.dto.Severity;
import com.nvh12.reaction.service.dto.alert.BruteForceAlert;
import com.nvh12.reaction.service.dto.alert.DDoSAlert;
import com.nvh12.reaction.service.dto.alert.TrafficAlert;
import com.nvh12.reaction.service.dto.alert.WebAttackAlert;
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
        when(resend.emails()).thenReturn(emails);
        when(alertProperties.getMail()).thenReturn(mail);
        when(mail.getFrom()).thenReturn("alerts@example.com");
        when(mail.getTo()).thenReturn("admin@example.com");
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
    void alert_forWebAttackAlert_includesLayerInBody() throws ResendException {
        WebAttackAlert alert = WebAttackAlert.builder()
                .detectionType(DetectionType.WEB_ATTACK)
                .sourceIp("5.5.5.5")
                .severity(Severity.MEDIUM)
                .detectedAt(Instant.now())
                .layerTriggered("rule_engine")
                .build();

        service.alert(alert);

        assertThat(captureOptions().getHtml()).contains("rule_engine");
    }

    @Test
    void alert_forTrafficAlert_includesOnlyActiveFlagsInBody() throws ResendException {
        MethodFlags flags = new MethodFlags();
        flags.setZScore(true);
        flags.setSeasonal(true);

        TrafficAlert alert = TrafficAlert.builder()
                .detectionType(DetectionType.TRAFFIC)
                .sourceIp("2.2.2.2")
                .severity(Severity.LOW)
                .detectedAt(Instant.now())
                .methodFlags(flags)
                .build();

        service.alert(alert);

        String html = captureOptions().getHtml();
        assertThat(html).contains("Z-Score").contains("Seasonal");
        assertThat(html).doesNotContain("IQR").doesNotContain("EMA");
    }

    @Test
    void alert_forBruteForceAlert_includesDestinationInBody() throws ResendException {
        BruteForceAlert alert = BruteForceAlert.builder()
                .detectionType(DetectionType.BRUTE_FORCE)
                .sourceIp("9.9.9.9")
                .severity(Severity.HIGH)
                .detectedAt(Instant.now())
                .destIp("192.168.1.1")
                .destPort(22)
                .build();

        service.alert(alert);

        assertThat(captureOptions().getHtml()).contains("192.168.1.1:22");
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
