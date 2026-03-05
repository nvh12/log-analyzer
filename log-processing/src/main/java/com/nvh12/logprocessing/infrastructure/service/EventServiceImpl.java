package com.nvh12.logprocessing.infrastructure.service;

import com.nvh12.logprocessing.domain.model.NormalizedLog;
import com.nvh12.logprocessing.domain.service.EventService;
import org.springframework.stereotype.Component;

@Component
public class EventServiceImpl implements EventService {
    @Override
    public void publish(NormalizedLog log) {

    }
}
