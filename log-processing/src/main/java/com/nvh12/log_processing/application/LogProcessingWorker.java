package com.nvh12.log_processing.application;

import com.nvh12.log_processing.domain.model.ProcessingResult;
import com.nvh12.log_processing.domain.model.RawLog;
import com.nvh12.log_processing.domain.service.EventService;
import com.nvh12.log_processing.domain.service.FailedLogRepository;
import com.nvh12.log_processing.domain.service.LogProcessingService;
import com.nvh12.log_processing.domain.service.ProcessedLogRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class LogProcessingWorker {

    private final LogProcessingService logProcessingService;
    private final EventService eventService;
    private final ProcessedLogRepository processedLogRepository;
    private final FailedLogRepository failedLogRepository;
    private final Counter successCounter;
    private final Counter failureCounter;

    public LogProcessingWorker(LogProcessingService logProcessingService,
                               EventService eventService,
                               ProcessedLogRepository processedLogRepository,
                               FailedLogRepository failedLogRepository,
                               MeterRegistry meterRegistry) {
        this.logProcessingService = logProcessingService;
        this.eventService = eventService;
        this.processedLogRepository = processedLogRepository;
        this.failedLogRepository = failedLogRepository;
        this.successCounter = meterRegistry.counter("logs.processed", "result", "success");
        this.failureCounter = meterRegistry.counter("logs.processed", "result", "failure");
    }

    public void processSingleLog(RawLog raw) {
        try {
            ProcessingResult result = logProcessingService.process(raw);
            boolean isNew = processedLogRepository.save(result);
            if (isNew) {
                eventService.publish(result);
            } else {
                log.info("Skipping publish for already-processed log {} (duplicate source_log_id)", raw.getId());
            }
            successCounter.increment();
        } catch (Exception e) {
            log.warn("Failed to process log {}. Sending to DLQ.", raw.getId(), e);
            failedLogRepository.save(raw, e.getMessage());
            failureCounter.increment();
        }
    }
}
