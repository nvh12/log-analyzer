package com.nvh12.reaction.infrastructure.mq;

import com.nvh12.reaction.infrastructure.config.RabbitMqConfig;
import com.nvh12.reaction.service.dto.ReactionAction;
import com.nvh12.reaction.service.dto.Severity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReactionResultPublisherTest {

    @Mock RabbitTemplate rabbitTemplate;

    ReactionResultPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new ReactionResultPublisher(rabbitTemplate);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> capturePayload() {
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(rabbitTemplate).convertAndSend(eq(RabbitMqConfig.EXCHANGE_REACTION_RESULTS), eq(""), captor.capture());
        return captor.getValue();
    }

    @Test
    void publish_sendsMessageToReactionResultsExchange() {
        Instant ts = Instant.now();

        publisher.publish(1L, ReactionAction.BLOCK, "1.2.3.4", Severity.HIGH, ts);

        Map<String, Object> payload = capturePayload();
        assertThat(payload.get("reaction_id")).isEqualTo(1L);
        assertThat(payload.get("action")).isEqualTo("BLOCK");
        assertThat(payload.get("target")).isEqualTo("1.2.3.4");
        assertThat(payload.get("ts")).isEqualTo(ts.toString());
    }

    @Test
    void publish_nullTarget_sendsEmptyStringTarget() {
        publisher.publish(1L, ReactionAction.SCALE_UP, null, Severity.LOW, Instant.now());

        assertThat(capturePayload().get("target")).isEqualTo("");
    }

    @Test
    void publish_scaleUpAction_ttlIsZero() {
        publisher.publish(1L, ReactionAction.SCALE_UP, "1.2.3.4", Severity.CRITICAL, Instant.now());

        assertThat(capturePayload().get("ttl_seconds")).isEqualTo(0L);
    }

    @Test
    void publish_ttlSecondsBySeverity() {
        publisher.publish(1L, ReactionAction.BLOCK, "1.2.3.4", Severity.LOW, Instant.now());
        assertThat(capturePayload().get("ttl_seconds")).isEqualTo(5 * 60L);

        publisher.publish(2L, ReactionAction.BLOCK, "1.2.3.4", Severity.MEDIUM, Instant.now());
        assertThat(capturePayload().get("ttl_seconds")).isEqualTo(30 * 60L);

        publisher.publish(3L, ReactionAction.BLOCK, "1.2.3.4", Severity.HIGH, Instant.now());
        assertThat(capturePayload().get("ttl_seconds")).isEqualTo(2 * 3600L);

        publisher.publish(4L, ReactionAction.BLOCK, "1.2.3.4", Severity.CRITICAL, Instant.now());
        assertThat(capturePayload().get("ttl_seconds")).isEqualTo(24 * 3600L);
    }

    @Test
    void publish_noneSeverity_doesNotThrowAndDoesNotSend() {
        assertThatNoException().isThrownBy(() ->
                publisher.publish(1L, ReactionAction.BLOCK, "1.2.3.4", Severity.NONE, Instant.now()));

        verify(rabbitTemplate, org.mockito.Mockito.never())
                .convertAndSend(any(String.class), any(String.class), any(Object.class));
    }

    @Test
    void publish_rabbitTemplateThrows_doesNotPropagate() {
        doThrow(new RuntimeException("broker down")).when(rabbitTemplate)
                .convertAndSend(any(String.class), any(String.class), any(Object.class));

        assertThatNoException().isThrownBy(() ->
                publisher.publish(1L, ReactionAction.BLOCK, "1.2.3.4", Severity.HIGH, Instant.now()));
    }
}
