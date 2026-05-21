package com.nvh12.dashboard.infrastructure.sse;

import com.nvh12.dashboard.application.port.BroadcastPort;
import com.nvh12.dashboard.application.port.SseRegistrar;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Component
@RequiredArgsConstructor
public class SseEmitterRegistry implements BroadcastPort, SseRegistrar {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final ObjectMapper objectMapper;

    public SseEmitter register() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        return emitter;
    }

    public void broadcast(String eventType, Object data) {
        if (emitters.isEmpty()) return;
        String payload;
        try {
            payload = objectMapper.writeValueAsString(data);
        } catch (JacksonException e) {
            log.error("Failed to serialize SSE payload for event {}: {}", eventType, e.getMessage());
            return;
        }
        SseEmitter.SseEventBuilder event = SseEmitter.event().name(eventType).data(payload);
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(event);
            } catch (IOException e) {
                emitters.remove(emitter);
            }
        }
    }

    @Scheduled(fixedDelay = 15_000)
    public void heartbeat() {
        broadcast("heartbeat", Map.of("ts", Instant.now().toString()));
    }
}
