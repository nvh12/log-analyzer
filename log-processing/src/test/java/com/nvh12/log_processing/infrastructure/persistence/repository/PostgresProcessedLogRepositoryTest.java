package com.nvh12.log_processing.infrastructure.persistence.repository;

import com.nvh12.log_processing.domain.model.HttpMethod;
import com.nvh12.log_processing.domain.model.NormalizedFlowRecord;
import com.nvh12.log_processing.domain.model.NormalizedLog;
import com.nvh12.log_processing.domain.model.ProcessingResult;
import com.nvh12.log_processing.infrastructure.persistence.entity.NormalizedFlowEntity;
import com.nvh12.log_processing.infrastructure.persistence.entity.NormalizedHttpEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PostgresProcessedLogRepositoryTest {

    @Mock
    private NormalizedHttpJpaRepository httpRepository;
    @Mock
    private NormalizedFlowJpaRepository flowRepository;

    private PostgresProcessedLogRepository repository;

    @BeforeEach
    void setUp() {
        repository = new PostgresProcessedLogRepository(httpRepository, flowRepository);
    }

    @Test
    void saveHttpVariantMapsToHttpEntity() {
        NormalizedLog log = new NormalizedLog(
                1688000000.0, "1.2.3.4", HttpMethod.GET, "/api", 200, 1024, "q=1", Map.of("Host", "localhost"), "Mozilla", "http://ref");
        ProcessingResult result = new ProcessingResult.Http(log);

        repository.save(result);

        ArgumentCaptor<NormalizedHttpEntity> captor = ArgumentCaptor.forClass(NormalizedHttpEntity.class);
        verify(httpRepository).save(captor.capture());

        NormalizedHttpEntity saved = captor.getValue();
        assertThat(saved.getTimestamp()).isEqualTo(1688000000.0);
        assertThat(saved.getIp()).isEqualTo("1.2.3.4");
        assertThat(saved.getMethod()).isEqualTo(HttpMethod.GET);
        assertThat(saved.getUrl()).isEqualTo("/api");
        assertThat(saved.getStatusCode()).isEqualTo(200);
        assertThat(saved.getResponseSize()).isEqualTo(1024);
        assertThat(saved.getQueryString()).isEqualTo("q=1");
        assertThat(saved.getHeaders()).containsEntry("Host", "localhost");
        assertThat(saved.getUserAgent()).isEqualTo("Mozilla");
        assertThat(saved.getReferer()).isEqualTo("http://ref");
    }

    @Test
    void saveFlowVariantMapsToFlowEntity() {
        NormalizedFlowRecord record = new NormalizedFlowRecord(
                1688000000.0, "10.0.0.1", "10.0.0.2", 1234, 80, Map.of("f1", 1.0, "f2", 0.5));
        ProcessingResult result = new ProcessingResult.Flow(record);

        repository.save(result);

        ArgumentCaptor<NormalizedFlowEntity> captor = ArgumentCaptor.forClass(NormalizedFlowEntity.class);
        verify(flowRepository).save(captor.capture());

        NormalizedFlowEntity saved = captor.getValue();
        assertThat(saved.getTimestamp()).isEqualTo(1688000000.0);
        assertThat(saved.getSourceIp()).isEqualTo("10.0.0.1");
        assertThat(saved.getDestIp()).isEqualTo("10.0.0.2");
        assertThat(saved.getSourcePort()).isEqualTo(1234);
        assertThat(saved.getDestPort()).isEqualTo(80);
        assertThat(saved.getFeatures()).containsEntry("f1", 1.0).containsEntry("f2", 0.5);
    }
}
