package com.nvh12.log_processing.infrastructure.persistence.repository;

import com.nvh12.log_processing.AbstractContainerIT;
import com.nvh12.log_processing.domain.model.HttpMethod;
import com.nvh12.log_processing.domain.model.NormalizedFlowRecord;
import com.nvh12.log_processing.domain.model.NormalizedLog;
import com.nvh12.log_processing.domain.model.ProcessingResult;
import com.nvh12.log_processing.domain.service.EventService;
import com.nvh12.log_processing.domain.service.FailedLogRepository;
import com.nvh12.log_processing.domain.service.LogProcessingService;
import com.nvh12.log_processing.domain.service.QueueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PostgresProcessedLogRepositoryIT extends AbstractContainerIT {

    @Autowired
    private PostgresProcessedLogRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private LogProcessingService logProcessingService;

    @MockitoBean
    private EventService eventService;

    @MockitoBean
    private QueueService queueService;

    @MockitoBean
    private FailedLogRepository failedLogRepository;


    @Test
    void saveHttpResult_persistsToDatabase() {
        NormalizedLog log = new NormalizedLog(
                1714730000.0, "1.2.3.4", HttpMethod.GET, "/test", 200, 1024,
                "q=1", Map.of("X-Test", "Value"), "UA", "Ref"
        );
        ProcessingResult.Http result = new ProcessingResult.Http(log);

        repository.save(result);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM normalized_http WHERE ip = '1.2.3.4'", Integer.class);
        assertThat(count).isEqualTo(1);
        
        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT * FROM normalized_http WHERE ip = '1.2.3.4'");
        assertThat(row.get("processed_at")).isNotNull();
        assertThat(row.get("url")).isEqualTo("/test");
    }

    @Test
    void saveFlowResult_persistsToDatabase() {
        NormalizedFlowRecord record = new NormalizedFlowRecord(
                1714730000.0, "10.0.0.1", "10.0.0.2", 12345, 80,
                Map.of("f1", 1.0, "f2", 0.5)
        );
        ProcessingResult.Flow result = new ProcessingResult.Flow(record);

        repository.save(result);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM normalized_flow WHERE source_ip = '10.0.0.1'", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void nullHeadersMap_saveSucceeds() {
        NormalizedLog log = new NormalizedLog(
                1714730000.0, "1.2.3.4", HttpMethod.GET, "/test", 200, 1024,
                null, null, null, null
        );
        repository.save(new ProcessingResult.Http(log));

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM normalized_http", Integer.class);
        assertThat(count).isEqualTo(1);
    }
}
