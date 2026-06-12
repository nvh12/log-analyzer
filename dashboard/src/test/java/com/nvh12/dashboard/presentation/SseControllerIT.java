package com.nvh12.dashboard.presentation;

import com.nvh12.dashboard.AbstractContainerIT;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SseControllerIT extends AbstractContainerIT {

    @Test
    void stream_registersEmitter_andStartsAsyncResponse() throws Exception {
        mockMvc.perform(get("/api/stream"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());
    }
}
