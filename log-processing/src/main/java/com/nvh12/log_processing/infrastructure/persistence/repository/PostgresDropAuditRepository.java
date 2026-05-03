package com.nvh12.log_processing.infrastructure.persistence.repository;

import com.nvh12.log_processing.domain.model.DropReason;
import com.nvh12.log_processing.domain.model.FailedLogEntry;
import com.nvh12.log_processing.domain.model.RawLog;
import com.nvh12.log_processing.domain.service.DropAuditRepository;
import com.nvh12.log_processing.infrastructure.persistence.entity.DropAuditEntity;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@AllArgsConstructor
public class PostgresDropAuditRepository implements DropAuditRepository {

    private final DropAuditJpaRepository jpaRepository;

    @Override
    public void record(FailedLogEntry entry, DropReason reason) {
        RawLog raw = entry.rawLog();
        jpaRepository.save(DropAuditEntity.builder()
                .logId(raw.getId())
                .source(raw.getSource())
                .rawMessage(raw.getRawMessage())
                .receivedAt(raw.getReceivedAt())
                .dropReason(reason)
                .failureReason(entry.failureReason())
                .retryCount(entry.retryCount())
                .failedAt(entry.failedAt())
                .droppedAt(Instant.now())
                .build());
    }

    @Override
    public void recordDeadLetter(String rawBody, String logId, String failureReason) {
        jpaRepository.save(DropAuditEntity.builder()
                .logId(logId)
                .rawMessage(rawBody)
                .dropReason(DropReason.DEAD_LETTERED)
                .failureReason(failureReason)
                .droppedAt(Instant.now())
                .build());
    }
}
