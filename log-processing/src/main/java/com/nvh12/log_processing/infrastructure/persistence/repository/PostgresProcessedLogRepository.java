package com.nvh12.log_processing.infrastructure.persistence.repository;

import com.nvh12.log_processing.domain.model.NormalizedFlowRecord;
import com.nvh12.log_processing.domain.model.NormalizedLog;
import com.nvh12.log_processing.domain.model.ProcessingResult;
import com.nvh12.log_processing.domain.service.ProcessedLogRepository;
import com.nvh12.log_processing.infrastructure.persistence.entity.NormalizedFlowEntity;
import com.nvh12.log_processing.infrastructure.persistence.entity.NormalizedHttpEntity;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@AllArgsConstructor
public class PostgresProcessedLogRepository implements ProcessedLogRepository {

    private final NormalizedHttpJpaRepository httpRepository;
    private final NormalizedFlowJpaRepository flowRepository;

    @Override
    public void save(ProcessingResult result) {
        switch (result) {
            case ProcessingResult.Http http -> saveHttp(http.log());
            case ProcessingResult.Flow flow -> saveFlow(flow.record());
        }
    }

    private void saveHttp(NormalizedLog log) {
        NormalizedHttpEntity entity = NormalizedHttpEntity.builder()
                .timestamp(log.timestamp())
                .ip(log.ip())
                .method(log.method())
                .url(log.url())
                .statusCode(log.statusCode())
                .responseSize(log.responseSize())
                .queryString(log.queryString())
                .headers(log.headers())
                .userAgent(log.userAgent())
                .referer(log.referer())
                .build();
        httpRepository.save(entity);
    }

    private void saveFlow(NormalizedFlowRecord record) {
        NormalizedFlowEntity entity = NormalizedFlowEntity.builder()
                .timestamp(record.timestamp())
                .sourceIp(record.sourceIp())
                .destIp(record.destIp())
                .sourcePort(record.sourcePort())
                .destPort(record.destPort())
                .features(record.features())
                .build();
        flowRepository.save(entity);
    }
}
