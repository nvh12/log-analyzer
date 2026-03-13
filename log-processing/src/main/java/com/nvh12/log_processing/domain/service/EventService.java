package com.nvh12.log_processing.domain.service;

import com.nvh12.log_processing.domain.model.NormalizedLog;

public interface EventService {
    void publish(NormalizedLog log);
}
