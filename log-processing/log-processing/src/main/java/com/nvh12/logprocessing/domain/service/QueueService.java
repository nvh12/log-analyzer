package com.nvh12.logprocessing.domain.service;

import com.nvh12.logprocessing.domain.model.RawLog;

import java.util.List;

public interface QueueService {
    boolean enqueue(RawLog rawLog);

    List<RawLog> dequeueBatch(int batchSize);

    RawLog dequeue();
}
