package com.nvh12.reaction.service.impl;

import com.nvh12.reaction.config.AlertProperties;
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
            message = buildMessage(alert);
        } catch (MessagingException e) {
            log.error("Failed to build alert email [type={} ip={} severity={}]: {}",
                    alert.getDetectionType(), alert.getSourceIp(), alert.getSeverity(), e.getMessage());
            return;
        }
        Retry.run(3, 1000, MailException.class,
                () -> mailSender.send(message),
                e -> log.error("Alert email failed after retries [type={} ip={} severity={}]: {}",
                        alert.getDetectionType(), alert.getSourceIp(), alert.getSeverity(), e.getMessage()));
    }

    private MimeMessage buildMessage(Alert alert) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(alertProperties.getMail().getFrom());
        helper.setTo(alertProperties.getMail().getTo());
        helper.setSubject(AlertHtmlTemplate.buildSubject(alert));
        helper.setText(AlertHtmlTemplate.buildHtmlBody(alert), true);
        return message;
    }
}
