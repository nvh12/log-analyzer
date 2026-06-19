package com.nvh12.log_processing.domain.service;

import com.nvh12.log_processing.domain.model.ProcessingResult;

public interface ProcessedLogRepository {
    /**
     * @return true if this result was newly persisted; false if it was already
     *         processed (duplicate source_log_id) and the save was a no-op.
     */
    boolean save(ProcessingResult result);
}
