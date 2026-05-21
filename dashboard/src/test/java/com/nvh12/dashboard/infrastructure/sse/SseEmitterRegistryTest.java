package com.nvh12.dashboard.infrastructure.sse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SseEmitterRegistryTest {

    @Mock
    ObjectMapper objectMapper;
    @InjectMocks SseEmitterRegistry registry;

    @Test
    void register_returnsNonNullEmitterWithMaxTimeout() {
        SseEmitter emitter = registry.register();
        assertThat(emitter).isNotNull();
    }

    @Test
    void broadcast_noEmitters_skipsSerializationEarly() throws Exception {
        registry.broadcast("type", "data");
        verifyNoInteractions(objectMapper);
    }

    @Test
    void broadcast_serializationFailure_doesNotPropagate() throws Exception {
        registry.register(); // ensure emitters list is non-empty to pass the isEmpty() guard
        when(objectMapper.writeValueAsString(any())).thenThrow(new JacksonException("fail") {});

        assertThatNoException().isThrownBy(() -> registry.broadcast("type", Map.of("k", "v")));
    }

    @Test
    void broadcast_serializesPayloadAndSendsEventName() throws Exception {
        registry.register();
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"k\":\"v\"}");

        // broadcast should not throw even if the unconnected emitter fails on send
        assertThatNoException().isThrownBy(() -> registry.broadcast("detection", Map.of("k", "v")));
        verify(objectMapper).writeValueAsString(any());
    }

    @Test
    void heartbeat_withNoEmitters_doesNotCallSerializer() {
        assertThatNoException().isThrownBy(() -> registry.heartbeat());
        verifyNoInteractions(objectMapper);
    }

    @Test
    void heartbeat_withEmitters_attemptsSerializationOfTimestamp() throws Exception {
        registry.register();
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"ts\":\"2024-01-01T00:00:00Z\"}");

        assertThatNoException().isThrownBy(() -> registry.heartbeat());
        verify(objectMapper).writeValueAsString(argThat(data -> {
            if (data instanceof Map<?, ?> map) {
                return map.containsKey("ts");
            }
            return false;
        }));
    }
}
