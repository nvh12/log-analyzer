package com.nvh12.log_processing.infrastructure.service;

import com.nvh12.log_processing.domain.model.NormalizedLog;
import com.nvh12.log_processing.domain.model.RawLog;
import com.nvh12.log_processing.domain.service.LogProcessingService;
import org.springframework.stereotype.Component;

@Component
public class LogProcessingServiceImpl implements LogProcessingService {
    @Override
    public NormalizedLog process(RawLog rawLog) {
        return null;
    }
}
