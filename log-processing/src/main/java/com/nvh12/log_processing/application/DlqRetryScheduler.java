package com.nvh12.log_processing.application;

import com.nvh12.log_processing.domain.model.DropReason;
import com.nvh12.log_processing.domain.model.FailedLogEntry;
import com.nvh12.log_processing.domain.model.ProcessingResult;
import com.nvh12.log_processing.domain.service.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;

@Component
@Slf4j
public class DlqRetryScheduler {

    public record Config(int maxRetries, int retryBatchSize, long retryDelayMs, long retryJitterMs) {}

    private final FailedLogRepository failedLogRepository;
    private final LogProcessingService logProcessingService;
    private final EventService eventService;
    private final ProcessedLogRepository processedLogRepository;
    private final DropAuditRepository dropAuditRepository;
    private final int maxRetries;
    private final int retryBatchSize;
    private final long retryDelayMs;
    private final long retryJitterMs;
    private final Counter retrySuccessCounter;
    private final Counter retryFailedCounter;
    private final Counter retryExhaustedCounter;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            r -> {
                Thread t = new Thread(r, "DlqRetryScheduler");
                t.setDaemon(true);
                return t;
            });

    public DlqRetryScheduler(FailedLogRepository failedLogRepository,
                             LogProcessingService logProcessingService,
                             EventService eventService,
                             ProcessedLogRepository processedLogRepository,
                             DropAuditRepository dropAuditRepository,
                             Config config,
                             MeterRegistry meterRegistry) {
        this.failedLogRepository = failedLogRepository;
        this.logProcessingService = logProcessingService;
        this.eventService = eventService;
        this.processedLogRepository = processedLogRepository;
        this.dropAuditRepository = dropAuditRepository;
        this.maxRetries = config.maxRetries();
        this.retryBatchSize = config.retryBatchSize();
        this.retryDelayMs = config.retryDelayMs();
        this.retryJitterMs = config.retryJitterMs();
        this.retrySuccessCounter = meterRegistry.counter("logs.retry", "result", "success");
        this.retryFailedCounter = meterRegistry.counter("logs.retry", "result", "failed");
        this.retryExhaustedCounter = meterRegistry.counter("logs.retry", "result", "exhausted");
    }

    @PostConstruct
    void start() {
        scheduleNext();
    }

    @PreDestroy
    void stop() {
        scheduler.shutdown();
        try {
            scheduler.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void scheduleNext() {
        if (scheduler.isShutdown()) return;
        long jitter = ThreadLocalRandom.current().nextLong(-retryJitterMs, retryJitterMs + 1);
        try {
            scheduler.schedule(() -> {
                try {
                    retryFailedLogs();
                } catch (Exception e) {
                    log.error("Unexpected error in DLQ retry cycle — rescheduling", e);
                } finally {
                    scheduleNext();
                }
            }, retryDelayMs + jitter, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            log.debug("DlqRetryScheduler shut down — not scheduling next retry");
        }
    }

    void retryFailedLogs() {
        List<FailedLogEntry> entries = failedLogRepository.getFailedLogEntries(retryBatchSize);

        for (FailedLogEntry entry : entries) {
            if (entry.retryCount() >= maxRetries) {
                log.error("Permanently dropping log after {} retries. id={}, reason={}",
                        maxRetries, entry.rawLog().getId(), entry.failureReason());
                retryExhaustedCounter.increment();
                try {
                    dropAuditRepository.record(entry, DropReason.RETRY_EXHAUSTED);
                } catch (Exception ae) {
                    log.error("Failed to persist exhausted retry to audit store. id={}", entry.rawLog().getId(), ae);
                }
                continue;
            }

            try {
                ProcessingResult result = logProcessingService.process(entry.rawLog());
                processedLogRepository.save(result);
                eventService.publish(result);
                log.debug("Retry succeeded for log id={}", entry.rawLog().getId());
                retrySuccessCounter.increment();
            } catch (Exception e) {
                int nextRetry = entry.retryCount() + 1;
                log.warn("Retry {}/{} failed for log id={}. Reason: {}",
                        nextRetry, maxRetries, entry.rawLog().getId(), e.getMessage());
                failedLogRepository.saveEntry(
                        FailedLogEntry.builder()
                                .rawLog(entry.rawLog())
                                .failureReason(e.getMessage())
                                .failedAt(Instant.now())
                                .retryCount(nextRetry)
                                .build());
                retryFailedCounter.increment();
            }
        }
    }
}
