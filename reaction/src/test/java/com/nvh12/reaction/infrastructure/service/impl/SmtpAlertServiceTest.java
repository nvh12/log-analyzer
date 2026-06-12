package com.nvh12.reaction.infrastructure.service.impl;

import com.nvh12.reaction.infrastructure.config.AlertProperties;
import com.nvh12.reaction.service.dto.DetectionType;
import com.nvh12.reaction.service.dto.Severity;
import com.nvh12.reaction.service.dto.alert.Alert;
import com.nvh12.reaction.service.dto.alert.DDoSAlert;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SmtpAlertServiceTest {

    @Mock JavaMailSender mailSender;
    @Mock AlertProperties alertProperties;

    SmtpAlertService service;

    @Mock AlertProperties.Mail mail;

    @BeforeEach
    void setUp() {
        lenient().when(alertProperties.getMail()).thenReturn(mail);
        lenient().when(mail.getFrom()).thenReturn("alerts@example.com");
        lenient().when(mail.getTo()).thenReturn("admin@example.com");
        lenient().when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        service = new SmtpAlertService(mailSender, alertProperties);
    }

    private MimeMessage captureMessage() {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }

    private static String extractBody(MimeMessage msg) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        msg.writeTo(out);
        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    void alert_setsFromToAndSubject() throws Exception {
        DDoSAlert alert = DDoSAlert.builder()
                .detectionType(DetectionType.DDOS)
                .sourceIp("1.2.3.4")
                .severity(Severity.CRITICAL)
                .detectedAt(Instant.now())
                .build();

        service.alert(alert);

        MimeMessage msg = captureMessage();
        assertThat(((InternetAddress) msg.getFrom()[0]).getAddress()).isEqualTo("alerts@example.com");
        assertThat(((InternetAddress) msg.getAllRecipients()[0]).getAddress()).isEqualTo("admin@example.com");
        assertThat(msg.getSubject()).isEqualTo("[CRITICAL] DDOS detected from 1.2.3.4");
    }

    @Test
    void alert_forDDoSAlert_includesDestinationInBody() throws Exception {
        DDoSAlert alert = DDoSAlert.builder()
                .detectionType(DetectionType.DDOS)
                .sourceIp("1.2.3.4")
                .severity(Severity.HIGH)
                .detectedAt(Instant.now())
                .destIp("10.0.0.1")
                .destPort(80)
                .build();

        service.alert(alert);

        assertThat(extractBody(captureMessage())).contains("10.0.0.1:80");
    }

    @Test
    void alertBatch_sendsOneEmailWithBatchSubjectAndBody() throws Exception {
        List<Alert> alerts = List.of(
                DDoSAlert.builder()
                        .detectionType(DetectionType.DDOS).sourceIp("1.1.1.1")
                        .severity(Severity.HIGH).detectedAt(Instant.now()).build(),
                DDoSAlert.builder()
                        .detectionType(DetectionType.DDOS).sourceIp("2.2.2.2")
                        .severity(Severity.CRITICAL).detectedAt(Instant.now()).build()
        );

        service.alertBatch(alerts);

        MimeMessage msg = captureMessage();
        assertThat(((InternetAddress) msg.getFrom()[0]).getAddress()).isEqualTo("alerts@example.com");
        assertThat(((InternetAddress) msg.getAllRecipients()[0]).getAddress()).isEqualTo("admin@example.com");
        assertThat(msg.getSubject()).isEqualTo("[CRITICAL] 2 DDOS alerts detected");
        String body = extractBody(msg);
        assertThat(body).contains("1.1.1.1").contains("2.2.2.2");
    }

    @Test
    void alertBatch_emptyList_sendsNothing() {
        service.alertBatch(List.of());
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void alertBatch_whenSendThrows_retriesAndDoesNotRethrow() {
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(MimeMessage.class));

        List<Alert> alerts = List.of(
                DDoSAlert.builder()
                        .detectionType(DetectionType.DDOS).sourceIp("1.1.1.1")
                        .severity(Severity.LOW).detectedAt(Instant.now()).build()
        );

        assertDoesNotThrow(() -> service.alertBatch(alerts));
        verify(mailSender, times(3)).send(any(MimeMessage.class));
    }

    @Test
    void alert_whenSendThrows_retriesAndDoesNotRethrow() {
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(MimeMessage.class));

        DDoSAlert alert = DDoSAlert.builder()
                .detectionType(DetectionType.DDOS)
                .sourceIp("1.1.1.1")
                .severity(Severity.LOW)
                .detectedAt(Instant.now())
                .build();

        assertDoesNotThrow(() -> service.alert(alert));
        verify(mailSender, times(3)).send(any(MimeMessage.class));
    }

    @Test
    void alert_succeedsAfterTransientFailure() {
        doThrow(new MailSendException("timeout"))
                .doNothing()
                .when(mailSender).send(any(MimeMessage.class));

        DDoSAlert alert = DDoSAlert.builder()
                .detectionType(DetectionType.DDOS)
                .sourceIp("1.1.1.1")
                .severity(Severity.LOW)
                .detectedAt(Instant.now())
                .build();

        assertDoesNotThrow(() -> service.alert(alert));
        verify(mailSender, times(2)).send(any(MimeMessage.class));
    }
}
