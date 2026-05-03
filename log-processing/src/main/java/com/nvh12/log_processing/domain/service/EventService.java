package com.nvh12.log_processing.domain.service;

import com.nvh12.log_processing.domain.model.ProcessingResult;

public interface EventService {
    void publish(ProcessingResult result);
}
