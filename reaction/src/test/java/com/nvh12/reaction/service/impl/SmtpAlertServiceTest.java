package com.nvh12.reaction.service.impl;

import com.nvh12.reaction.config.AlertProperties;
import com.nvh12.reaction.service.dto.DetectionType;
import com.nvh12.reaction.service.dto.MethodFlags;
import com.nvh12.reaction.service.dto.Severity;
import com.nvh12.reaction.service.dto.alert.BruteForceAlert;
import com.nvh12.reaction.service.dto.alert.DDoSAlert;
import com.nvh12.reaction.service.dto.alert.TrafficAlert;
import com.nvh12.reaction.service.dto.alert.WebAttackAlert;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SmtpAlertServiceTest {

    @Mock JavaMailSender mailSender;
    @Mock AlertProperties alertProperties;

    SmtpAlertService service;

    @Mock AlertProperties.Mail mail;

    @BeforeEach
    void setUp() {
        when(alertProperties.getMail()).thenReturn(mail);
        when(mail.getFrom()).thenReturn("alerts@example.com");
        when(mail.getTo()).thenReturn("admin@example.com");
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
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
    void alert_forWebAttackAlert_includesLayerInBody() throws Exception {
        WebAttackAlert alert = WebAttackAlert.builder()
                .detectionType(DetectionType.WEB_ATTACK)
                .sourceIp("5.5.5.5")
                .severity(Severity.MEDIUM)
                .detectedAt(Instant.now())
                .layerTriggered("rule_engine")
                .build();

        service.alert(alert);

        assertThat(extractBody(captureMessage())).contains("rule_engine");
    }

    @Test
    void alert_forTrafficAlert_includesOnlyActiveFlagsInBody() throws Exception {
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

        String body = extractBody(captureMessage());
        assertThat(body).contains("Z-Score").contains("Seasonal");
        assertThat(body).doesNotContain("IQR").doesNotContain("EMA");
    }

    @Test
    void alert_forBruteForceAlert_includesDestinationInBody() throws Exception {
        BruteForceAlert alert = BruteForceAlert.builder()
                .detectionType(DetectionType.BRUTE_FORCE)
                .sourceIp("9.9.9.9")
                .severity(Severity.HIGH)
                .detectedAt(Instant.now())
                .destIp("192.168.1.1")
                .destPort(22)
                .build();

        service.alert(alert);

        assertThat(extractBody(captureMessage())).contains("192.168.1.1:22");
    }
}
