package com.nvh12.log_processing.domain.service;

import com.nvh12.log_processing.domain.model.ProcessingResult;
import com.nvh12.log_processing.domain.model.RawLog;

public interface LogProcessingService {
    ProcessingResult process(RawLog rawLog);
}
