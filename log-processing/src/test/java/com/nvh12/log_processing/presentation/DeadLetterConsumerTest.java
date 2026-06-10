package com.nvh12.log_processing.presentation;

import com.nvh12.log_processing.domain.service.DropAuditRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DeadLetterConsumerTest {

    @Mock
    private DropAuditRepository dropAuditRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private DeadLetterConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new DeadLetterConsumer(dropAuditRepository, objectMapper);
    }

    private Message message(String body, List<Map<String, ?>> xDeath) {
        var builder = MessageBuilder.withBody(body.getBytes(StandardCharsets.UTF_8));
        if (xDeath != null) {
            builder.setHeader("x-death", xDeath);
        }
        return builder.build();
    }

    @Test
    void usesReasonFromFirstXDeathEntry() {
        Message message = message("{\"id\":\"id-1\"}",
                List.of(Map.of("reason", "expired"), Map.of("reason", "rejected")));

        consumer.onDeadLetter(message);

        verify(dropAuditRepository).recordDeadLetter("{\"id\":\"id-1\"}", "id-1", "expired");
    }

    @Test
    void missingXDeathHeaderUsesUnknownReason() {
        Message message = message("{\"id\":\"id-2\"}", null);

        consumer.onDeadLetter(message);

        verify(dropAuditRepository).recordDeadLetter("{\"id\":\"id-2\"}", "id-2", "unknown");
    }

    @Test
    void xDeathEntryMissingReasonKeyUsesUnknown() {
        Message message = message("{\"id\":\"id-3\"}", List.of(Map.of("queue", "raw-log-dlq")));

        consumer.onDeadLetter(message);

        verify(dropAuditRepository).recordDeadLetter("{\"id\":\"id-3\"}", "id-3", "unknown");
    }

    @Test
    void malformedJsonBodyExtractsNullLogId() {
        Message message = message("not json", null);

        consumer.onDeadLetter(message);

        verify(dropAuditRepository).recordDeadLetter("not json", null, "unknown");
    }

    @Test
    void jsonWithoutIdFieldExtractsNullLogId() {
        Message message = message("{\"message\":\"no id field\"}", null);

        consumer.onDeadLetter(message);

        verify(dropAuditRepository).recordDeadLetter("{\"message\":\"no id field\"}", null, "unknown");
    }

    @Test
    void recordDeadLetterThrows_doesNotPropagate() {
        Message message = message("{\"id\":\"id-err\"}", null);
        doThrow(new RuntimeException("audit down")).when(dropAuditRepository)
                .recordDeadLetter(anyString(), anyString(), anyString());

        assertThatNoException().isThrownBy(() -> consumer.onDeadLetter(message));
    }
}
