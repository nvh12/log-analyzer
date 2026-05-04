package com.nvh12.log_processing.infrastructure.polling;

import com.nvh12.log_processing.application.LogProcessingWorker;
import com.nvh12.log_processing.domain.model.RawLog;
import com.nvh12.log_processing.domain.service.QueueService;
import com.nvh12.log_processing.infrastructure.config.LogProcessingProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LogProcessingPollerTest {

    @Mock
    private QueueService queueService;
    @Mock
    private LogProcessingWorker worker;

    private ThreadPoolTaskExecutor executor;
    private LogProcessingPoller poller;

    private static final LogProcessingProperties PROPERTIES = new LogProcessingProperties(
            10, 1, 10000, 40, 10000, 2000, 3, 30000L, 5000L,
            new LogProcessingProperties.ThreadPool(2, 4, 10, 5),
            new LogProcessingProperties.Validation(45, 2048, 512));

    @BeforeEach
    void setUpExecutor() {
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(10);
        executor.initialize();
    }

    @AfterEach
    void tearDown() {
        if (poller != null) {
            poller.stop();
        }
        executor.shutdown();
    }

    private RawLog makeRawLog(String id) {
        return RawLog.builder()
                .id(id)
                .rawMessage("1.2.3.4 - - [01/Jul/1995:00:00:01 +0000] \"GET / HTTP/1.0\" 200 100")
                .source("http")
                .receivedAt(Instant.now())
                .build();
    }

    @Test
    void dispatchesBatchToWorker() {
        RawLog rawLog = makeRawLog("id-1");

        when(queueService.dequeueBatch(anyInt()))
                .thenReturn(List.of(rawLog))
                .thenReturn(List.of());

        poller = new LogProcessingPoller(queueService, worker, executor, PROPERTIES);
        poller.start();

        verify(worker, timeout(2000)).processSingleLog(rawLog);
    }

    @Test
    void dispatchesEachLogInBatchIndependently() {
        RawLog log1 = makeRawLog("id-batch-1");
        RawLog log2 = makeRawLog("id-batch-2");

        when(queueService.dequeueBatch(anyInt()))
                .thenReturn(List.of(log1, log2))
                .thenReturn(List.of());

        poller = new LogProcessingPoller(queueService, worker, executor, PROPERTIES);
        poller.start();

        verify(worker, timeout(2000).times(2)).processSingleLog(any());
    }

    @Test
    void doesNotDispatchWhenQueueIsEmpty() {
        when(queueService.dequeueBatch(anyInt())).thenReturn(List.of());

        poller = new LogProcessingPoller(queueService, worker, executor, PROPERTIES);
        poller.start();

        verify(worker, after(200).never()).processSingleLog(any());
    }
}
