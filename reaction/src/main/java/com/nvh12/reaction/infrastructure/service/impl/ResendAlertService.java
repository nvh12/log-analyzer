package com.nvh12.reaction.infrastructure.service.impl;

import com.nvh12.reaction.infrastructure.config.AlertProperties;
import com.nvh12.reaction.service.AlertChannel;
import com.nvh12.reaction.service.dto.alert.Alert;
import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "alert.provider", havingValue = "resend")
public class ResendAlertService implements AlertChannel {

    private final Resend resend;
    private final AlertProperties alertProperties;

    @Override
    public void alert(Alert alert) {
        send(AlertHtmlTemplate.buildSubject(alert),
                AlertHtmlTemplate.buildHtmlBody(alert),
                "type=%s ip=%s severity=%s".formatted(
                        alert.getDetectionType(), alert.getSourceIp(), alert.getSeverity()));
    }

    @Override
    public void alertBatch(List<Alert> alerts) {
        if (alerts.isEmpty()) return;
        send(AlertHtmlTemplate.buildBatchSubject(alerts),
                AlertHtmlTemplate.buildBatchHtmlBody(alerts),
                "type=%s count=%d".formatted(alerts.get(0).getDetectionType(), alerts.size()));
    }

    private void send(String subject, String htmlBody, String context) {
        CreateEmailOptions options = CreateEmailOptions.builder()
                .from(alertProperties.getMail().getFrom())
                .to(List.of(alertProperties.getMail().getTo()))
                .subject(subject)
                .html(htmlBody)
                .build();
        Retry.run(3, 1000, RuntimeException.class,
                () -> {
                    try {
                        resend.emails().send(options);
                    } catch (ResendException e) {
                        throw new RuntimeException(e);
                    }
                },
                e -> log.error("Alert email failed after retries [{}]: {}", context,
                        e.getCause() != null ? e.getCause().getMessage() : e.getMessage()));
    }
}
