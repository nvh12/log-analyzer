package com.nvh12.dashboard.infrastructure.persistence;

import com.nvh12.dashboard.AbstractContainerIT;
import com.nvh12.dashboard.domain.DetectionType;
import com.nvh12.dashboard.domain.NetworkLayer;
import com.nvh12.dashboard.domain.Severity;
import com.nvh12.dashboard.infrastructure.persistence.entity.DetectionResultEntity;
import com.nvh12.dashboard.infrastructure.persistence.entity.NormalizedFlowEntity;
import com.nvh12.dashboard.infrastructure.persistence.repository.jpa.JpaDetectionResultRepository;
import com.nvh12.dashboard.infrastructure.persistence.repository.jpa.JpaFlowLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JsonConverterIT extends AbstractContainerIT {

    @Autowired JpaFlowLogRepository jpaFlowLogRepository;
    @Autowired JpaDetectionResultRepository jpaDetectionResultRepository;

    // ── NormalizedFlowEntity / JsonDoubleMapConverter ────────────────────────

    @Test
    void flowEntity_withFeatures_roundTripsJsonbColumn() {
        Map<String, Double> features = Map.of(
                "Flow Duration",          12345.0,
                "Total Fwd Packets",      10.0,
                "Bwd Packet Length Max",  0.0,
                "Flow Bytes/s",           4096.5);

        NormalizedFlowEntity saved = jpaFlowLogRepository.save(
                NormalizedFlowEntity.builder()
                        .sourceIp("10.0.0.1").destIp("10.0.0.2")
                        .sourcePort(54321).destPort(80)
                        .features(features)
                        .processedAt(Instant.now()).build());

        NormalizedFlowEntity reloaded = jpaFlowLogRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getFeatures()).isEqualTo(features);
    }

    @Test
    void flowEntity_withNullFeatures_roundTripsNullColumn() {
        NormalizedFlowEntity saved = jpaFlowLogRepository.save(
                NormalizedFlowEntity.builder()
                        .sourceIp("10.0.0.1").destIp("10.0.0.2")
                        .sourcePort(54321).destPort(80)
                        .processedAt(Instant.now()).build());

        NormalizedFlowEntity reloaded = jpaFlowLogRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getFeatures()).isNull();
    }

    // ── DetectionResultEntity / JsonBooleanMapConverter ──────────────────────

    @Test
    void detectionResult_withMethodFlags_roundTripsJsonbColumn() {
        Map<String, Boolean> methodFlags = Map.of(
                "GET",    true,
                "POST",   false,
                "PUT",    true,
                "DELETE", false);

        DetectionResultEntity saved = jpaDetectionResultRepository.save(
                DetectionResultEntity.builder()
                        .detectionType(DetectionType.TRAFFIC)
                        .severity(Severity.MEDIUM).anomaly(true)
                        .networkLayer(NetworkLayer.HTTP)
                        .methodFlags(methodFlags)
                        .detectedAt(Instant.now()).build());

        DetectionResultEntity reloaded = jpaDetectionResultRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getMethodFlags()).isEqualTo(methodFlags);
    }

    @Test
    void detectionResult_withNullMethodFlags_roundTripsNullColumn() {
        DetectionResultEntity saved = jpaDetectionResultRepository.save(
                DetectionResultEntity.builder()
                        .detectionType(DetectionType.DDOS)
                        .severity(Severity.HIGH).anomaly(true)
                        .networkLayer(NetworkLayer.FLOW)
                        .detectedAt(Instant.now()).build());

        DetectionResultEntity reloaded = jpaDetectionResultRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getMethodFlags()).isNull();
    }

    @Test
    void detectionResult_withSeverityNone_roundTripsEnum() {
        DetectionResultEntity saved = jpaDetectionResultRepository.save(
                DetectionResultEntity.builder()
                        .detectionType(DetectionType.TRAFFIC)
                        .severity(Severity.NONE).anomaly(false)
                        .networkLayer(NetworkLayer.HTTP)
                        .detectedAt(Instant.now()).build());

        DetectionResultEntity reloaded = jpaDetectionResultRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getSeverity()).isEqualTo(Severity.NONE);
    }
}
