package com.nvh12.log_processing.infrastructure.service;

import com.nvh12.log_processing.domain.model.NormalizedFlowRecord;
import com.nvh12.log_processing.domain.model.NormalizedLog;
import com.nvh12.log_processing.domain.model.ProcessingResult;
import com.nvh12.log_processing.infrastructure.config.RabbitMqConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.Map;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EventServiceImplTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private EventServiceImpl eventService;

    @Test
    void routesHttpResultToHttpQueue() {
        NormalizedLog log = new NormalizedLog(
                1688000000.0, "1.2.3.4", "GET", "/index.html",
                200, 512, "", null, Map.of(), null, null);
        ProcessingResult result = new ProcessingResult.Http(log);

        eventService.publish(result);

        verify(rabbitTemplate).convertAndSend(RabbitMqConfig.QUEUE_NORMALIZED_HTTP, log);
    }

    @Test
    void routesFlowResultToFlowQueue() {
        NormalizedFlowRecord record = new NormalizedFlowRecord(
                1688000000.0, "10.0.0.1", "192.168.1.1", 54321, 443,
                Map.of("Flow Bytes/s", 1500.0));
        ProcessingResult result = new ProcessingResult.Flow(record);

        eventService.publish(result);

        verify(rabbitTemplate).convertAndSend(RabbitMqConfig.QUEUE_NORMALIZED_FLOW, record);
    }
}
