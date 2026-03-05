package com.nvh12.logprocessing.domain.service;

import com.nvh12.logprocessing.domain.model.RawLog;

public interface QueueService {
    void enqueue(RawLog rawLog);

    RawLog dequeue();
}
