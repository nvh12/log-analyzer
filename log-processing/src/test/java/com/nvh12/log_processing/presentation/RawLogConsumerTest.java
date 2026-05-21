package com.nvh12.log_processing.presentation;

import com.nvh12.log_processing.domain.model.LogSource;
import com.nvh12.log_processing.domain.model.RawLog;
import com.nvh12.log_processing.domain.service.QueueService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RawLogConsumerTest {

    @Mock
    private QueueService queueService;

    @InjectMocks
    private RawLogConsumer rawLogConsumer;

    private RawLog makeRawLog(String id) {
        return RawLog.builder()
                .id(id)
                .rawMessage("1.2.3.4 - - [01/Jul/1995:00:00:01 +0000] \"GET / HTTP/1.0\" 200 100")
                .source(LogSource.HTTP)
                .receivedAt(Instant.now())
                .build();
    }

    @Test
    void enqueuesMessageOnSuccess() {
        RawLog rawLog = makeRawLog("test-id");
        when(queueService.enqueue(rawLog)).thenReturn(true);

        rawLogConsumer.onMessage(rawLog);

        verify(queueService).enqueue(rawLog);
    }

    @Test
    void deadLettersMessageWhenQueueIsFull() {
        RawLog rawLog = makeRawLog("test-id");
        when(queueService.enqueue(rawLog)).thenReturn(false);

        assertThatThrownBy(() -> rawLogConsumer.onMessage(rawLog))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }
}
