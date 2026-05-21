package com.nvh12.reaction.service.impl;

import com.nvh12.reaction.config.AlertProperties;
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
        CreateEmailOptions options = CreateEmailOptions.builder()
                .from(alertProperties.getMail().getFrom())
                .to(List.of(alertProperties.getMail().getTo()))
                .subject(AlertHtmlTemplate.buildSubject(alert))
                .html(AlertHtmlTemplate.buildHtmlBody(alert))
                .build();
        Retry.run(3, 1000, RuntimeException.class,
                () -> {
                    try {
                        resend.emails().send(options);
                    } catch (ResendException e) {
                        throw new RuntimeException(e);
                    }
                },
                e -> log.error("Alert email failed after retries [type={} ip={} severity={}]: {}",
                        alert.getDetectionType(), alert.getSourceIp(), alert.getSeverity(),
                        e.getCause() != null ? e.getCause().getMessage() : e.getMessage()));
    }
}
