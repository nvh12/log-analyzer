package com.nvh12.logprocessing.infrastructure.service;

import com.nvh12.logprocessing.domain.model.RawLog;
import com.nvh12.logprocessing.domain.service.QueueService;
import org.springframework.stereotype.Component;

@Component
public class QueueServiceImpl implements QueueService {
    @Override
    public void enqueue(RawLog rawLog) {

    }

    @Override
    public RawLog dequeue() {
        return null;
    }
}
