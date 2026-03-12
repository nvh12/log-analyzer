package com.nvh12.logprocessing.application;

import com.nvh12.logprocessing.domain.model.NormalizedLog;
import com.nvh12.logprocessing.domain.model.RawLog;
import com.nvh12.logprocessing.domain.service.FailedLogRepository;
import com.nvh12.logprocessing.domain.service.EventService;
import com.nvh12.logprocessing.domain.service.LogProcessingService;
import com.nvh12.logprocessing.domain.service.QueueService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class LogProcessingWorker {
  private final LogProcessingService logProcessingService;
  private final QueueService queueService;
  private final FailedLogRepository failedLogRepository;
  private final EventService eventService;
  private final ThreadPoolTaskExecutor executor;
  private volatile Thread pollerThread;

  private final AtomicBoolean running = new AtomicBoolean(false);
  private static final int BATCH_SIZE = 10;
  private static final int BACKPRESSURE_THRESHOLD = 40;

  public LogProcessingWorker(
      LogProcessingService logProcessingService,
      QueueService queueService,
      FailedLogRepository failedLogRepository,
      EventService eventService) {
    this.logProcessingService = logProcessingService;
    this.queueService = queueService;
    this.failedLogRepository = failedLogRepository;
    this.eventService = eventService;

    // dynamic thread pool config
    executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(12); // scales under load
    executor.setQueueCapacity(50); // internal buffer before scaling to max size
    executor.setThreadNamePrefix("LogWorker-");

    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

    // ensures clean shutdown: waits for tasks to finish
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(30);
    executor.initialize();
  }

  @PostConstruct
  public void start() {
    running.set(true);

    pollerThread = new Thread(this::pollLoop);
    pollerThread.setName("LogPoller");
    pollerThread.start();
  }

  private void pollLoop() {
    ThreadPoolExecutor pool = executor.getThreadPoolExecutor();

    while (running.get()) {
      try {
        if (pool.getQueue().size() > BACKPRESSURE_THRESHOLD) {
          Thread.sleep(20);
          continue;
        }

        List<RawLog> batch = queueService.dequeueBatch(BATCH_SIZE);

        if (batch.isEmpty()) {
          Thread.sleep(50);
          continue;
        }

        for (RawLog raw : batch) {
          executor.execute(() -> processSingleLog(raw));
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        log.warn("Poller interrupted, shutting down");
        break;
      } catch (Exception e) {
        log.error("Polling failed", e);
      }
    }
  }

  private void processSingleLog(RawLog raw) {
    try {
      NormalizedLog normalized = logProcessingService.process(raw);
      eventService.publish(normalized);

    } catch (Exception e) {
      // dead letter queue (DLQ) Logic
      log.warn("Failed to process log {}. Sending to DLQ.", raw.getId(), e);
      failedLogRepository.save(raw, e.getMessage());
    }
  }

  @PreDestroy
  public void stop() throws InterruptedException {
    log.info("Shutting down LogProcessingWorker...");
    running.set(false);
    if (pollerThread != null) pollerThread.join(5000);
    executor.shutdown();
  }
}
