package com.nvh12.log_processing.application;

import com.nvh12.log_processing.domain.model.FailedLogEntry;
import com.nvh12.log_processing.domain.model.NormalizedLog;
import com.nvh12.log_processing.domain.model.ProcessingResult;
import com.nvh12.log_processing.domain.model.RawLog;
import com.nvh12.log_processing.domain.service.*;
import com.nvh12.log_processing.infrastructure.config.LogProcessingProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DlqRetrySchedulerTest {

    @Mock
    private FailedLogRepository failedLogRepository;
    @Mock
    private LogProcessingService logProcessingService;
    @Mock
    private EventService eventService;
    @Mock
    private ProcessedLogRepository processedLogRepository;
    @Mock
    private DropAuditRepository dropAuditRepository;

    private DlqRetryScheduler scheduler;
    private int maxRetries;

    @BeforeEach
    void setUp() {
        LogProcessingProperties properties = new LogProcessingProperties(
                10, 1, 10000, 40, 10000, 2000, 3, 30000L, 5000L,
                new LogProcessingProperties.ThreadPool(2, 4, 10, 5),
                new LogProcessingProperties.Validation(45, 2048, 512));
        maxRetries = properties.maxRetries();
        scheduler = new DlqRetryScheduler(
                failedLogRepository, logProcessingService, eventService, processedLogRepository,
                dropAuditRepository, properties, new SimpleMeterRegistry());
    }

    private RawLog makeRawLog(String id) {
        return RawLog.builder()
                .id(id)
                .rawMessage("1.2.3.4 - - [01/Jul/1995:00:00:01 +0000] \"GET / HTTP/1.0\" 200 100")
                .source("http")
                .receivedAt(Instant.now())
                .build();
    }

    private FailedLogEntry failedEntry(RawLog rawLog, int retryCount) {
        return FailedLogEntry.builder()
                .rawLog(rawLog)
                .failureReason("parse error")
                .failedAt(Instant.now())
                .retryCount(retryCount)
                .build();
    }

    private ProcessingResult successResult() {
        NormalizedLog normalized = new NormalizedLog(
                1688000000.0, "1.2.3.4", "GET", "/", 200, 100, "", null, Map.of(), null, null);
        return new ProcessingResult.Http(normalized);
    }

    @Test
    void successfulRetryPublishesEventAndDoesNotRequeue() {
        RawLog rawLog = makeRawLog("id-ok");
        ProcessingResult result = successResult();

        when(failedLogRepository.getFailedLogEntries(anyInt())).thenReturn(List.of(failedEntry(rawLog, 0)));
        when(logProcessingService.process(rawLog)).thenReturn(result);

        scheduler.retryFailedLogs();

        verify(processedLogRepository).save(result);
        verify(eventService).publish(result);
        verify(failedLogRepository, never()).saveEntry(any());
    }

    @Test
    void failedRetryIncrementsRetryCountAndRequeuesBackToDlq() {
        RawLog rawLog = makeRawLog("id-fail");

        when(failedLogRepository.getFailedLogEntries(anyInt())).thenReturn(List.of(failedEntry(rawLog, 1)));
        when(logProcessingService.process(rawLog)).thenThrow(new IllegalArgumentException("still bad"));

        scheduler.retryFailedLogs();

        ArgumentCaptor<FailedLogEntry> captor = ArgumentCaptor.forClass(FailedLogEntry.class);
        verify(failedLogRepository).saveEntry(captor.capture());
        assertThat(captor.getValue().retryCount()).isEqualTo(2);
        assertThat(captor.getValue().failureReason()).isEqualTo("still bad");
        assertThat(captor.getValue().rawLog()).isSameAs(rawLog);
        verify(processedLogRepository, never()).save(any());
        verify(eventService, never()).publish(any());
    }

    @Test
    void entryAtMaxRetriesIsPermanentlyDropped() {
        RawLog rawLog = makeRawLog("id-maxed");

        when(failedLogRepository.getFailedLogEntries(anyInt()))
                .thenReturn(List.of(failedEntry(rawLog, maxRetries)));

        scheduler.retryFailedLogs();

        verify(logProcessingService, never()).process(any());
        verify(eventService, never()).publish(any());
        verify(failedLogRepository, never()).saveEntry(any());
    }

    @Test
    void emptyDlqDoesNothing() {
        when(failedLogRepository.getFailedLogEntries(anyInt())).thenReturn(List.of());

        scheduler.retryFailedLogs();

        verify(logProcessingService, never()).process(any());
        verify(eventService, never()).publish(any());
        verify(failedLogRepository, never()).saveEntry(any());
    }

    @Test
    void mixedBatchProcessesEachEntryIndependently() {
        RawLog goodLog = makeRawLog("id-good");
        RawLog badLog = makeRawLog("id-bad");
        RawLog maxedLog = makeRawLog("id-maxed");
        ProcessingResult result = successResult();

        when(failedLogRepository.getFailedLogEntries(anyInt())).thenReturn(List.of(
                failedEntry(goodLog, 0),
                failedEntry(badLog, 2),
                failedEntry(maxedLog, maxRetries)));
        when(logProcessingService.process(goodLog)).thenReturn(result);
        when(logProcessingService.process(badLog)).thenThrow(new RuntimeException("transient"));

        scheduler.retryFailedLogs();

        verify(processedLogRepository).save(result);
        verify(eventService).publish(result);
        ArgumentCaptor<FailedLogEntry> captor = ArgumentCaptor.forClass(FailedLogEntry.class);
        verify(failedLogRepository).saveEntry(captor.capture());
        assertThat(captor.getValue().rawLog()).isSameAs(badLog);
        assertThat(captor.getValue().retryCount()).isEqualTo(3);
        verify(logProcessingService, never()).process(maxedLog);
    }
}
