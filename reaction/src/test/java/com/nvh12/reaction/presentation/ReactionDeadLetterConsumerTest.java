package com.nvh12.reaction.presentation;

import com.nvh12.reaction.service.DroppedReactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReactionDeadLetterConsumerTest {

    @Mock
    private DroppedReactionService droppedReactionService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ReactionDeadLetterConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new ReactionDeadLetterConsumer(droppedReactionService, objectMapper);
    }

    private Message message(String body, List<Map<String, ?>> xDeath) {
        var builder = MessageBuilder.withBody(body.getBytes(StandardCharsets.UTF_8));
        if (xDeath != null) {
            builder.setHeader("x-death", xDeath);
        }
        return builder.build();
    }

    @Test
    void extractsFieldsFromWellFormedBodyAndUsesFirstXDeathReason() {
        String body = "{\"detection_type\":\"DDOS\",\"source_ip\":\"1.2.3.4\",\"severity\":\"HIGH\","
                + "\"detected_at\":\"2026-01-01T00:00:00Z\"}";
        Message message = message(body, List.of(Map.of("reason", "rejected"), Map.of("reason", "expired")));

        consumer.onDeadLetter(message);

        verify(droppedReactionService).record("DDOS", "1.2.3.4", "HIGH",
                Instant.parse("2026-01-01T00:00:00Z"), "rejected", body);
    }

    @Test
    void missingXDeathHeaderUsesUnknownReason() {
        String body = "{\"detection_type\":\"BRUTE_FORCE\",\"source_ip\":\"5.6.7.8\",\"severity\":\"MEDIUM\"}";
        Message message = message(body, null);

        consumer.onDeadLetter(message);

        verify(droppedReactionService).record("BRUTE_FORCE", "5.6.7.8", "MEDIUM", null, "unknown", body);
    }

    @Test
    void malformedBody_stillRecordsRawPayloadWithNullFields() {
        String body = "not json";
        Message message = message(body, null);

        consumer.onDeadLetter(message);

        verify(droppedReactionService).record(isNull(), isNull(), isNull(), isNull(), eq("unknown"), eq(body));
    }

    @Test
    void recordThrows_doesNotPropagate() {
        String body = "{\"detection_type\":\"WEB_ATTACK\"}";
        Message message = message(body, null);
        doThrow(new RuntimeException("db down")).when(droppedReactionService)
                .record(any(), any(), any(), any(), any(), any());

        assertThatNoException().isThrownBy(() -> consumer.onDeadLetter(message));
    }
}
