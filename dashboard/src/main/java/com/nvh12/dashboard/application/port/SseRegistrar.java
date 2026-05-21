package com.nvh12.dashboard.application.port;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface SseRegistrar {
    SseEmitter register();
}
