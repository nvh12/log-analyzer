package com.nvh12.log_processing.domain.service;

import com.nvh12.log_processing.domain.model.DropReason;
import com.nvh12.log_processing.domain.model.FailedLogEntry;

public interface DropAuditRepository {
    void record(FailedLogEntry entry, DropReason reason);

    void recordDeadLetter(String rawBody, String logId, String failureReason);
}
