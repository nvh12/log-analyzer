package com.nvh12.log_processing.domain.service;

import com.nvh12.log_processing.domain.model.RawLog;

import java.util.List;

public interface QueueService {
    boolean enqueue(RawLog rawLog);

    List<RawLog> dequeueBatch(int batchSize);

    RawLog dequeue();
}
