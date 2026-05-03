package com.nvh12.log_processing;

import com.nvh12.log_processing.infrastructure.polling.LogProcessingPoller;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class LogProcessingApplicationTests extends AbstractContainerIT {

    // Prevent LogProcessingPoller from running background tasks during context load test
    @MockitoBean
    LogProcessingPoller poller;

    @Test
    void contextLoads() {
    }
}
