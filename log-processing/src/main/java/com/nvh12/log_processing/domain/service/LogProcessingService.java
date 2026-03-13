package com.nvh12.log_processing.domain.service;

import com.nvh12.log_processing.domain.model.NormalizedLog;
import com.nvh12.log_processing.domain.model.RawLog;

public interface LogProcessingService {
    NormalizedLog process(RawLog rawLog);
}
