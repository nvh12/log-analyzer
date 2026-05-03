package com.nvh12.log_processing.infrastructure.service;

import com.nvh12.log_processing.AbstractContainerIT;
import com.nvh12.log_processing.domain.model.NormalizedFlowRecord;
import com.nvh12.log_processing.domain.model.NormalizedLog;
import com.nvh12.log_processing.domain.model.ProcessingResult;
import com.nvh12.log_processing.domain.service.DropAuditRepository;
import com.nvh12.log_processing.domain.service.FailedLogRepository;
import com.nvh12.log_processing.domain.service.LogProcessingService;
import com.nvh12.log_processing.domain.service.ProcessedLogRepository;
import com.nvh12.log_processing.domain.service.QueueService;
import com.nvh12.log_processing.infrastructure.config.RabbitMqConfig;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EventServiceImplIT extends AbstractContainerIT {

    @Autowired
    private EventServiceImpl eventService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @MockitoBean
    private LogProcessingService logProcessingService;

    @MockitoBean
    private ProcessedLogRepository processedLogRepository;

    @MockitoBean
    private QueueService queueService;

    @MockitoBean
    private FailedLogRepository failedLogRepository;

    @MockitoBean
    private DropAuditRepository dropAuditRepository;

    @Test
    void publishHttp_sendsToCorrectQueue() {
        NormalizedLog log = new NormalizedLog(
                1.0, "1.1.1.1", "POST", "/api", 201, 10,
                null, null, Map.of(), "UA", null
        );
        eventService.publish(new ProcessingResult.Http(log));

        NormalizedLog received = (NormalizedLog) rabbitTemplate.receiveAndConvert(RabbitMqConfig.QUEUE_NORMALIZED_HTTP, 5000);
        assertThat(received).isNotNull();
        assertThat(received.ip()).isEqualTo("1.1.1.1");
    }

    @Test
    void publishFlow_sendsToCorrectQueue() {
        NormalizedFlowRecord record = new NormalizedFlowRecord(
                2.0, "10.0.0.1", "10.0.0.2", 80, 8080, Map.of()
        );
        eventService.publish(new ProcessingResult.Flow(record));

        NormalizedFlowRecord received = (NormalizedFlowRecord) rabbitTemplate.receiveAndConvert(RabbitMqConfig.QUEUE_NORMALIZED_FLOW, 5000);
        assertThat(received).isNotNull();
        assertThat(received.sourceIp()).isEqualTo("10.0.0.1");
    }
}
