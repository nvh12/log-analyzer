package com.nvh12.logprocessing.infrastructure.service;

import com.nvh12.logprocessing.domain.model.NormalizedLog;
import com.nvh12.logprocessing.domain.model.RawLog;
import com.nvh12.logprocessing.domain.service.LogProcessingService;
import org.springframework.stereotype.Component;

@Component
public class LogProcessingServiceImpl implements LogProcessingService {
    @Override
    public NormalizedLog process(RawLog rawLog) {
        return null;
    }
}
