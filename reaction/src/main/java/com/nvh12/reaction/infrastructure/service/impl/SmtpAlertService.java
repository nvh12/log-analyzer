package com.nvh12.reaction.infrastructure.service.impl;

import com.nvh12.reaction.infrastructure.config.AlertProperties;
import com.nvh12.reaction.service.AlertChannel;
import com.nvh12.reaction.service.dto.alert.Alert;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "alert.provider", havingValue = "smtp", matchIfMissing = true)
public class SmtpAlertService implements AlertChannel {

    private final JavaMailSender mailSender;
    private final AlertProperties alertProperties;

    @Override
    public void alert(Alert alert) {
        MimeMessage message;
        try {
            message = buildMessage(
                    AlertHtmlTemplate.buildSubject(alert),
                    AlertHtmlTemplate.buildHtmlBody(alert));
        } catch (MessagingException e) {
            log.error("Failed to build alert email [type={} ip={} severity={}]: {}",
                    alert.getDetectionType(), alert.getSourceIp(), alert.getSeverity(), e.getMessage());
            return;
        }
        send(message, "type=%s ip=%s severity=%s".formatted(
                alert.getDetectionType(), alert.getSourceIp(), alert.getSeverity()));
    }

    @Override
    public void alertBatch(List<Alert> alerts) {
        if (alerts.isEmpty()) return;
        MimeMessage message;
        try {
            message = buildMessage(
                    AlertHtmlTemplate.buildBatchSubject(alerts),
                    AlertHtmlTemplate.buildBatchHtmlBody(alerts));
        } catch (MessagingException e) {
            log.error("Failed to build batch alert email [type={} count={}]: {}",
                    alerts.get(0).getDetectionType(), alerts.size(), e.getMessage());
            return;
        }
        send(message, "type=%s count=%d".formatted(alerts.get(0).getDetectionType(), alerts.size()));
    }

    private MimeMessage buildMessage(String subject, String htmlBody) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(alertProperties.getMail().getFrom());
        helper.setTo(alertProperties.getMail().getTo());
        helper.setSubject(subject);
        helper.setText(htmlBody, true);
        return message;
    }

    private void send(MimeMessage message, String context) {
        Retry.run(3, 1000, MailException.class,
                () -> mailSender.send(message),
                e -> log.error("Alert email failed after retries [{}]: {}", context, e.getMessage()));
    }
}
