package com.nvh12.log_processing.infrastructure.service;

import com.nvh12.log_processing.domain.model.NormalizedLog;
import com.nvh12.log_processing.domain.service.EventService;
import org.springframework.stereotype.Component;

@Component
public class EventServiceImpl implements EventService {
    @Override
    public void publish(NormalizedLog log) {

    }
}
