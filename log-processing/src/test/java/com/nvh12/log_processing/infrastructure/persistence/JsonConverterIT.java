package com.nvh12.log_processing.infrastructure.persistence;

import com.nvh12.log_processing.AbstractContainerIT;
import com.nvh12.log_processing.domain.model.HttpMethod;
import com.nvh12.log_processing.infrastructure.persistence.entity.NormalizedFlowEntity;
import com.nvh12.log_processing.infrastructure.persistence.entity.NormalizedHttpEntity;
import com.nvh12.log_processing.infrastructure.persistence.repository.NormalizedFlowJpaRepository;
import com.nvh12.log_processing.infrastructure.persistence.repository.NormalizedHttpJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JsonConverterIT extends AbstractContainerIT {

    @Autowired NormalizedFlowJpaRepository flowRepo;
    @Autowired NormalizedHttpJpaRepository httpRepo;

    // ── NormalizedFlowEntity / JsonDoubleMapConverter ─────────────────────────

    @Test
    void flowEntity_withFeatures_roundTripsJsonbColumn() {
        Map<String, Double> features = Map.of(
                "Flow Duration",       12345.0,
                "Total Fwd Packets",   10.0,
                "Bwd Pkt Length Max",  0.0,
                "Flow Bytes/s",        4096.5);

        NormalizedFlowEntity saved = flowRepo.save(NormalizedFlowEntity.builder()
                .timestamp(1714730000.0)
                .sourceIp("10.0.0.1").destIp("10.0.0.2")
                .sourcePort(54321).destPort(80)
                .features(features)
                .build());

        NormalizedFlowEntity reloaded = flowRepo.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getFeatures()).isEqualTo(features);
    }

    @Test
    void flowEntity_withEmptyFeatures_roundTripsJsonbColumn() {
        NormalizedFlowEntity saved = flowRepo.save(NormalizedFlowEntity.builder()
                .timestamp(1714730000.0)
                .sourceIp("10.0.0.2").destIp("10.0.0.3")
                .sourcePort(12345).destPort(443)
                .features(Map.of())
                .build());

        NormalizedFlowEntity reloaded = flowRepo.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getFeatures()).isEmpty();
    }

    // ── NormalizedHttpEntity / JsonStringMapConverter ─────────────────────────

    @Test
    void httpEntity_withHeaders_roundTripsJsonbColumn() {
        Map<String, String> headers = Map.of(
                "Content-Type",    "application/json",
                "X-Forwarded-For", "1.2.3.4",
                "User-Agent",      "TestClient/1.0");

        NormalizedHttpEntity saved = httpRepo.save(NormalizedHttpEntity.builder()
                .timestamp(1714730000.0)
                .ip("1.2.3.4").method(HttpMethod.GET)
                .url("/api/test").statusCode(200).responseSize(1024)
                .queryString("").headers(headers)
                .build());

        NormalizedHttpEntity reloaded = httpRepo.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getHeaders()).isEqualTo(headers);
    }

    @Test
    void httpEntity_withNullHeaders_roundTripsNullColumn() {
        NormalizedHttpEntity saved = httpRepo.save(NormalizedHttpEntity.builder()
                .timestamp(1714730000.0)
                .ip("1.2.3.5").method(HttpMethod.POST)
                .url("/api/other").statusCode(404).responseSize(0)
                .queryString("").headers(null)
                .build());

        NormalizedHttpEntity reloaded = httpRepo.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getHeaders()).isNull();
    }

    @Test
    void httpEntity_withEmptyHeaders_roundTripsJsonbColumn() {
        NormalizedHttpEntity saved = httpRepo.save(NormalizedHttpEntity.builder()
                .timestamp(1714730000.0)
                .ip("1.2.3.6").method(HttpMethod.DELETE)
                .url("/api/item/1").statusCode(204).responseSize(0)
                .queryString("").headers(Map.of())
                .build());

        NormalizedHttpEntity reloaded = httpRepo.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getHeaders()).isEmpty();
    }
}
