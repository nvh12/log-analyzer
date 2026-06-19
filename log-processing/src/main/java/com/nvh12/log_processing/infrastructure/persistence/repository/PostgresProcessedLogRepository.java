package com.nvh12.log_processing.infrastructure.persistence.repository;

import com.nvh12.log_processing.domain.model.NormalizedFlowRecord;
import com.nvh12.log_processing.domain.model.NormalizedLog;
import com.nvh12.log_processing.domain.model.ProcessingResult;
import com.nvh12.log_processing.domain.service.ProcessedLogRepository;
import com.nvh12.log_processing.infrastructure.persistence.entity.NormalizedFlowEntity;
import com.nvh12.log_processing.infrastructure.persistence.entity.NormalizedHttpEntity;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
@AllArgsConstructor
@Slf4j
public class PostgresProcessedLogRepository implements ProcessedLogRepository {

    private final NormalizedHttpJpaRepository httpRepository;
    private final NormalizedFlowJpaRepository flowRepository;

    @Override
    public boolean save(ProcessingResult result) {
        return switch (result) {
            case ProcessingResult.Http http -> saveHttp(http.log());
            case ProcessingResult.Flow flow -> saveFlow(flow.record());
        };
    }

    // Relies solely on the partial unique index on source_log_id (see V2__add_source_log_id.sql)
    // to detect duplicates — a pre-check-then-insert would be racy (two concurrent retries could
    // both pass the check) and redundant with this catch, which is correct on its own.
    private boolean saveHttp(NormalizedLog normalizedLog) {
        NormalizedHttpEntity entity = NormalizedHttpEntity.builder()
                .sourceLogId(normalizedLog.sourceLogId())
                .timestamp(normalizedLog.timestamp())
                .ip(normalizedLog.ip())
                .method(normalizedLog.method())
                .url(normalizedLog.url())
                .statusCode(normalizedLog.statusCode())
                .responseSize(normalizedLog.responseSize())
                .queryString(normalizedLog.queryString())
                .headers(normalizedLog.headers())
                .userAgent(normalizedLog.userAgent())
                .referer(normalizedLog.referer())
                .build();
        try {
            httpRepository.save(entity);
            return true;
        } catch (DataIntegrityViolationException e) {
            log.info("Duplicate source_log_id={} — already processed, skipping", normalizedLog.sourceLogId());
            return false;
        }
    }

    private boolean saveFlow(NormalizedFlowRecord record) {
        NormalizedFlowEntity entity = NormalizedFlowEntity.builder()
                .sourceLogId(record.sourceLogId())
                .timestamp(record.timestamp())
                .sourceIp(record.sourceIp())
                .destIp(record.destIp())
                .sourcePort(record.sourcePort())
                .destPort(record.destPort())
                .features(record.features())
                .build();
        try {
            flowRepository.save(entity);
            return true;
        } catch (DataIntegrityViolationException e) {
            log.info("Duplicate source_log_id={} — already processed, skipping", record.sourceLogId());
            return false;
        }
    }
}
