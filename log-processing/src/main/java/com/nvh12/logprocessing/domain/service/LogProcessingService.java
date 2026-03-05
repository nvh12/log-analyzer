package com.nvh12.logprocessing.domain.service;

import com.nvh12.logprocessing.domain.model.NormalizedLog;
import com.nvh12.logprocessing.domain.model.RawLog;

public interface LogProcessingService {
    NormalizedLog process(RawLog rawLog);
}
