package com.nvh12.log_processing.infrastructure.persistence.repository;

import com.nvh12.log_processing.AbstractContainerIT;
import com.nvh12.log_processing.domain.model.HttpMethod;
import com.nvh12.log_processing.domain.model.NormalizedLog;
import com.nvh12.log_processing.domain.model.ProcessingResult;
import com.nvh12.log_processing.domain.service.EventService;
import com.nvh12.log_processing.domain.service.FailedLogRepository;
import com.nvh12.log_processing.domain.service.LogProcessingService;
import com.nvh12.log_processing.domain.service.QueueService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

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
    void nullHeadersMap_saveSucceeds() {
        NormalizedLog log = new NormalizedLog(
                "it-null-headers", 1714730000.0, "1.2.3.4", HttpMethod.GET, "/test", 200, 1024,
                null, null, null, null
        );
        repository.save(new ProcessingResult.Http(log));

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM log_processing.normalized_http", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void duplicateSourceLogId_doesNotInsertSecondRow() {
        NormalizedLog log = new NormalizedLog(
                "dup-id", 1714730000.0, "1.2.3.4", HttpMethod.GET, "/test", 200, 1024,
                null, null, null, null
        );
        repository.save(new ProcessingResult.Http(log));
        repository.save(new ProcessingResult.Http(log));

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM log_processing.normalized_http WHERE source_log_id = 'dup-id'", Integer.class);
        assertThat(count).isEqualTo(1);
    }
}
