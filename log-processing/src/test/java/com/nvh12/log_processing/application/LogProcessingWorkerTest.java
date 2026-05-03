package com.nvh12.log_processing.application;

import com.nvh12.log_processing.domain.model.NormalizedLog;
import com.nvh12.log_processing.domain.model.ProcessingResult;
import com.nvh12.log_processing.domain.model.RawLog;
import com.nvh12.log_processing.domain.service.EventService;
import com.nvh12.log_processing.domain.service.FailedLogRepository;
import com.nvh12.log_processing.domain.service.LogProcessingService;
import com.nvh12.log_processing.domain.service.ProcessedLogRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LogProcessingWorkerTest {

    @Mock
    private LogProcessingService logProcessingService;
    @Mock
    private EventService eventService;
    @Mock
    private ProcessedLogRepository processedLogRepository;
    @Mock
    private FailedLogRepository failedLogRepository;

    private LogProcessingWorker worker;

    @BeforeEach
    void setUp() {
        worker = new LogProcessingWorker(
                logProcessingService, eventService, processedLogRepository, failedLogRepository, new SimpleMeterRegistry());
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
    void successfulLogIsProcessedAndPublished() {
        RawLog rawLog = makeRawLog("id-ok");
        NormalizedLog normalized = new NormalizedLog(
                1688000000.0, "1.2.3.4", "GET", "/", 200, 100, "", null, Map.of(), null, null);
        ProcessingResult result = new ProcessingResult.Http(normalized);

        when(logProcessingService.process(rawLog)).thenReturn(result);

        worker.processSingleLog(rawLog);

        verify(processedLogRepository).save(result);
        verify(eventService).publish(result);
        verify(failedLogRepository, never()).save(any(), any());
    }

    @Test
    void failedLogIsSentToDlqAndEventIsNotPublished() {
        RawLog rawLog = makeRawLog("id-fail");

        when(logProcessingService.process(rawLog))
                .thenThrow(new IllegalArgumentException("Unparseable CLF entry"));

        worker.processSingleLog(rawLog);

        verify(failedLogRepository).save(rawLog, "Unparseable CLF entry");
        verify(processedLogRepository, never()).save(any());
        verify(eventService, never()).publish(any());
    }
}
