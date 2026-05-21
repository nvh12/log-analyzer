package com.nvh12.log_processing.infrastructure.persistence.repository;

import com.nvh12.log_processing.AbstractContainerIT;
import com.nvh12.log_processing.domain.model.DropReason;
import com.nvh12.log_processing.domain.model.FailedLogEntry;
import com.nvh12.log_processing.domain.model.LogSource;
import com.nvh12.log_processing.domain.model.RawLog;
import com.nvh12.log_processing.domain.service.EventService;
import com.nvh12.log_processing.domain.service.FailedLogRepository;
import com.nvh12.log_processing.domain.service.LogProcessingService;
import com.nvh12.log_processing.domain.service.QueueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PostgresDropAuditRepositoryIT extends AbstractContainerIT {

    @Autowired
    private PostgresDropAuditRepository repository;

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

    @BeforeEach
    void truncate() {
        jdbcTemplate.execute("TRUNCATE drop_audit RESTART IDENTITY");
    }

    @Test
    void record_persistsWithCorrectReason() {
        RawLog raw = RawLog.builder().id("id-1").source(LogSource.HTTP).rawMessage("msg").receivedAt(Instant.now()).build();
        FailedLogEntry entry = new FailedLogEntry(raw, "fail", Instant.now(), 3);

        repository.record(entry, DropReason.RETRY_EXHAUSTED);

        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT * FROM drop_audit WHERE log_id = 'id-1'");
        assertThat(row.get("drop_reason")).isEqualTo("RETRY_EXHAUSTED");
        assertThat(row.get("retry_count")).isEqualTo(3);
        assertThat(row.get("raw_message")).isEqualTo("msg");
    }

    @Test
    void recordDeadLetter_persistsCorrectly() {
        repository.recordDeadLetter("raw-body", "id-dlq", "expired");

        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT * FROM drop_audit WHERE log_id = 'id-dlq'");
        assertThat(row.get("drop_reason")).isEqualTo("DEAD_LETTERED");
        assertThat(row.get("failure_reason")).isEqualTo("expired");
    }

    @Test
    void recordWithNullReceivedAt_succeeds() {
        RawLog raw = RawLog.builder().id("id-null").source(LogSource.HTTP).rawMessage("msg").receivedAt(null).build();
        FailedLogEntry entry = new FailedLogEntry(raw, "fail", Instant.now(), 0);

        repository.record(entry, DropReason.DLQ_OVERFLOW);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM drop_audit WHERE log_id = 'id-null'", Integer.class);
        assertThat(count).isEqualTo(1);
    }
}
