package com.nvh12.logprocessing.application;

import com.nvh12.logprocessing.domain.model.RawLog;
import com.nvh12.logprocessing.domain.service.QueueService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class IngestionService {
    private final QueueService queueService;

    public void ingest(RawLog rawLog) {
        queueService.enqueue(rawLog);
    }
}
