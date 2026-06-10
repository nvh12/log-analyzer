package com.nvh12.dashboard.infrastructure.persistence.mapper;

import com.nvh12.dashboard.application.DetectionDetailView;
import com.nvh12.dashboard.application.DetectionSummaryView;
import com.nvh12.dashboard.domain.DetectionType;
import com.nvh12.dashboard.domain.NetworkLayer;
import com.nvh12.dashboard.domain.Severity;
import com.nvh12.dashboard.infrastructure.persistence.entity.DetectionResultEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DetectionMapperTest {

    private DetectionResultEntity.DetectionResultEntityBuilder baseEntity() {
        return DetectionResultEntity.builder()
                .id(1L)
                .detectionType(DetectionType.TRAFFIC)
                .severity(Severity.HIGH)
                .anomaly(true)
                .confidence(0.9)
                .networkLayer(NetworkLayer.HTTP)
                .sourceIp("1.2.3.4")
                .destIp("5.6.7.8")
                .destPort(443)
                .logTimestamp(Instant.parse("2026-01-01T00:00:00Z"))
                .windowStart(Instant.parse("2025-12-31T23:55:00Z"))
                .windowEnd(Instant.parse("2026-01-01T00:00:00Z"))
                .detectedAt(Instant.parse("2026-01-01T00:00:01Z"));
    }

    @Test
    void toSummary_mapsAllFields() {
        DetectionResultEntity entity = baseEntity().build();

        DetectionSummaryView view = DetectionMapper.toSummary(entity);

        assertThat(view.id()).isEqualTo(1L);
        assertThat(view.detectionType()).isEqualTo(DetectionType.TRAFFIC);
        assertThat(view.severity()).isEqualTo(Severity.HIGH);
        assertThat(view.anomaly()).isTrue();
        assertThat(view.confidence()).isEqualTo(0.9);
        assertThat(view.sourceIp()).isEqualTo("1.2.3.4");
        assertThat(view.detectedAt()).isEqualTo(Instant.parse("2026-01-01T00:00:01Z"));
    }

    @Test
    void toDetail_trafficWithMethodFlags_includesMethodFlagsInPayload() {
        DetectionResultEntity entity = baseEntity()
                .methodFlags(Map.of("GET", true, "POST", false))
                .build();

        DetectionDetailView view = DetectionMapper.toDetail(entity);

        assertThat(view.payload()).containsKey("method_flags");
        assertThat(view.payload().get("method_flags")).isEqualTo(Map.of("GET", true, "POST", false));
        assertThat(view.id()).isEqualTo(1L);
        assertThat(view.networkLayer()).isEqualTo(NetworkLayer.HTTP);
        assertThat(view.destIp()).isEqualTo("5.6.7.8");
        assertThat(view.destPort()).isEqualTo(443);
    }

    @Test
    void toDetail_trafficWithNullMethodFlags_payloadIsEmpty() {
        DetectionResultEntity entity = baseEntity()
                .methodFlags(null)
                .build();

        DetectionDetailView view = DetectionMapper.toDetail(entity);

        assertThat(view.payload()).isEmpty();
    }

    @Test
    void toDetail_nonTrafficType_omitsMethodFlagsEvenIfPresent() {
        DetectionResultEntity entity = baseEntity()
                .detectionType(DetectionType.DDOS)
                .networkLayer(NetworkLayer.FLOW)
                .methodFlags(Map.of("GET", true))
                .build();

        DetectionDetailView view = DetectionMapper.toDetail(entity);

        assertThat(view.payload()).isEmpty();
        assertThat(view.detectionType()).isEqualTo(DetectionType.DDOS);
    }
}
