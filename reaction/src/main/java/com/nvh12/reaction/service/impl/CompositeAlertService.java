package com.nvh12.reaction.service.impl;

import com.nvh12.reaction.service.AlertChannel;
import com.nvh12.reaction.service.AlertService;
import com.nvh12.reaction.service.dto.alert.Alert;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Primary
@Service
@RequiredArgsConstructor
public class CompositeAlertService implements AlertService {

    private final List<AlertChannel> channels;

    @PostConstruct
    void validateChannels() {
        if (channels.isEmpty()) {
            log.warn("No AlertChannel beans are active — all alerts will be silently dropped. "
                    + "Check alert.provider and alert.discord.webhook-url configuration.");
        }
    }

    @Override
    public void alert(Alert alert) {
        for (AlertChannel channel : channels) {
            try {
                channel.alert(alert);
            } catch (Exception e) {
                log.error("Alert channel {} failed [type={} ip={} severity={}]: {}",
                        channel.getClass().getSimpleName(),
                        alert.getDetectionType(), alert.getSourceIp(), alert.getSeverity(), e.getMessage());
            }
        }
    }
}
