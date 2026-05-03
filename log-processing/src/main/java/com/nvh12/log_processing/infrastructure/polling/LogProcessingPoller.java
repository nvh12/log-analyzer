package com.nvh12.log_processing.infrastructure.polling;

import com.nvh12.log_processing.application.LogProcessingWorker;
import com.nvh12.log_processing.domain.model.RawLog;
import com.nvh12.log_processing.domain.service.QueueService;
import com.nvh12.log_processing.infrastructure.config.LogProcessingProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Slf4j
public class LogProcessingPoller implements SmartLifecycle {

    private final QueueService queueService;
    private final LogProcessingWorker worker;
    private final ThreadPoolTaskExecutor executor;
    private final int batchSize;
    private final int backpressureThreshold;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Thread pollerThread;

    public LogProcessingPoller(QueueService queueService,
                               LogProcessingWorker worker,
                               @Qualifier("logWorkerExecutor") ThreadPoolTaskExecutor executor,
                               LogProcessingProperties properties) {
        this.queueService = queueService;
        this.worker = worker;
        this.executor = executor;
        this.batchSize = properties.batchSize();
        this.backpressureThreshold = properties.backpressureThreshold();
    }

    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            log.info("Starting LogProcessingPoller...");
            pollerThread = new Thread(this::pollLoop);
            pollerThread.setName("LogPoller");
            pollerThread.start();
        }
    }

    @Override
    public void stop() {
        stop(() -> {});
    }

    @Override
    public void stop(Runnable callback) {
        if (running.compareAndSet(true, false)) {
            log.info("Shutting down LogProcessingPoller...");
            if (pollerThread != null) {
                pollerThread.interrupt();
                try {
                    pollerThread.join(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Poller shutdown interrupted");
                }
            }
        }
        callback.run();
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    private void pollLoop() {
        ThreadPoolExecutor pool = executor.getThreadPoolExecutor();

        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                if (pool.getQueue().size() > backpressureThreshold) {
                    Thread.sleep(20);
                    continue;
                }

                List<RawLog> batch = queueService.dequeueBatch(batchSize);

                if (batch.isEmpty()) {
                    Thread.sleep(50);
                    continue;
                }

                for (RawLog raw : batch) {
                    if (!running.get()) break;
                    executor.execute(() -> worker.processSingleLog(raw));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("Poller thread interrupted, exiting loop");
                break;
            } catch (Exception e) {
                if (running.get()) {
                    log.error("Polling failed", e);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        log.info("LogProcessingPoller loop terminated.");
    }
}
