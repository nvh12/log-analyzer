package com.nvh12.reaction.infrastructure.persistence;

import com.nvh12.reaction.AbstractContainerIT;
import com.nvh12.reaction.infrastructure.persistence.entity.ReactionLogEntity;
import com.nvh12.reaction.infrastructure.persistence.repository.ReactionLogRepository;
import com.nvh12.reaction.service.dto.DetectionType;
import com.nvh12.reaction.service.dto.NetworkLayer;
import com.nvh12.reaction.service.dto.ReactionAction;
import com.nvh12.reaction.service.dto.Severity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ReactionLogEntityIT extends AbstractContainerIT {

    @Autowired ReactionLogRepository repository;

    @Test
    void save_allEnumAndTimestampFields_roundTripsCorrectly() {
        Instant detectedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Instant reactedAt  = detectedAt.plusSeconds(1);

        ReactionLogEntity saved = repository.save(ReactionLogEntity.builder()
                .detectionType(DetectionType.DDOS)
                .sourceIp("10.0.0.1")
                .severity(Severity.HIGH)
                .action(ReactionAction.BLOCK)
                .networkLayer(NetworkLayer.FLOW)
                .detectedAt(detectedAt)
                .reactedAt(reactedAt)
                .build());

        ReactionLogEntity reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getDetectionType()).isEqualTo(DetectionType.DDOS);
        assertThat(reloaded.getSourceIp()).isEqualTo("10.0.0.1");
        assertThat(reloaded.getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(reloaded.getAction()).isEqualTo(ReactionAction.BLOCK);
        assertThat(reloaded.getNetworkLayer()).isEqualTo(NetworkLayer.FLOW);
        assertThat(reloaded.getDetectedAt()).isEqualTo(detectedAt);
        assertThat(reloaded.getReactedAt()).isEqualTo(reactedAt);
    }

    @Test
    void save_trafficReactionWithWindowFields_roundTripsCorrectly() {
        Instant now         = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Instant windowStart = now.minusSeconds(60);
        Instant windowEnd   = now;

        ReactionLogEntity saved = repository.save(ReactionLogEntity.builder()
                .detectionType(DetectionType.TRAFFIC)
                .sourceIp(null)
                .severity(Severity.MEDIUM)
                .action(ReactionAction.SCALE_UP)
                .networkLayer(NetworkLayer.HTTP)
                .detectedAt(now)
                .windowStart(windowStart)
                .windowEnd(windowEnd)
                .reactedAt(now.plusSeconds(1))
                .build());

        ReactionLogEntity reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getSourceIp()).isNull();
        assertThat(reloaded.getWindowStart()).isEqualTo(windowStart);
        assertThat(reloaded.getWindowEnd()).isEqualTo(windowEnd);
        assertThat(reloaded.getDetectionType()).isEqualTo(DetectionType.TRAFFIC);
        assertThat(reloaded.getAction()).isEqualTo(ReactionAction.SCALE_UP);
        assertThat(reloaded.getNetworkLayer()).isEqualTo(NetworkLayer.HTTP);
    }

    @Test
    void save_allDetectionTypes_enumColumnsPersistCorrectly() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        for (DetectionType type : DetectionType.values()) {
            ReactionLogEntity e = repository.save(ReactionLogEntity.builder()
                    .detectionType(type)
                    .sourceIp("1.2.3.4")
                    .severity(Severity.LOW)
                    .action(ReactionAction.RATE_LIMIT)
                    .networkLayer(NetworkLayer.HTTP)
                    .detectedAt(now)
                    .reactedAt(now)
                    .build());
            assertThat(repository.findById(e.getId()).orElseThrow().getDetectionType()).isEqualTo(type);
        }
    }

    @Test
    void save_allSeverityLevels_enumColumnsPersistCorrectly() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        for (Severity severity : Severity.values()) {
            ReactionLogEntity e = repository.save(ReactionLogEntity.builder()
                    .detectionType(DetectionType.WEB_ATTACK)
                    .sourceIp("5.6.7.8")
                    .severity(severity)
                    .action(ReactionAction.BLOCK)
                    .networkLayer(NetworkLayer.HTTP)
                    .detectedAt(now)
                    .reactedAt(now)
                    .build());
            assertThat(repository.findById(e.getId()).orElseThrow().getSeverity()).isEqualTo(severity);
        }
    }
}
