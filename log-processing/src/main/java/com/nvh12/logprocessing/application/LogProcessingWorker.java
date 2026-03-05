package com.nvh12.logprocessing.application;

import com.nvh12.logprocessing.domain.model.NormalizedLog;
import com.nvh12.logprocessing.domain.model.RawLog;
import com.nvh12.logprocessing.domain.service.EventService;
import com.nvh12.logprocessing.domain.service.LogProcessingService;
import com.nvh12.logprocessing.domain.service.QueueService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
@AllArgsConstructor
public class LogProcessingWorker {
    private final LogProcessingService logProcessingService;
    private final QueueService queueService;
    private final EventService eventService;

    private final ExecutorService executor =
            Executors.newFixedThreadPool(4);

    private volatile boolean running = true;

    @PostConstruct
    public void start() {
        for (int i = 0; i < 2; i++) {
            executor.submit(this::runLoop);
        }
    }

    private void runLoop() {
        while (running) {
            try {
                RawLog raw = queueService.dequeue(); // blocking call
                NormalizedLog normalized = logProcessingService.process(raw);
                eventService.publish(normalized);
            } catch (Exception e) {
                // log error, don't crash thread
                e.printStackTrace();
            }
        }
    }

    @PreDestroy
    public void stop() {
        running = false;
        executor.shutdown();
    }
}
