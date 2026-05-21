package com.nvh12.dashboard.infrastructure.mq;

import com.nvh12.dashboard.domain.ReactionAction;
import com.nvh12.dashboard.infrastructure.mq.dto.ReactionResultMessage;
import com.nvh12.dashboard.infrastructure.sse.SseEmitterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReactionResultListenerTest {

    @Mock SseEmitterRegistry registry;
    @InjectMocks ReactionResultListener listener;

    @Test
    void onReaction_nullMessage_doesNotBroadcast() {
        listener.onReaction(null);
        verifyNoInteractions(registry);
    }

    @Test
    void onReaction_validMessage_broadcastsReactionEvent() {
        ReactionResultMessage msg = new ReactionResultMessage(42L, ReactionAction.BLOCK, "1.2.3.4", 1800L, Instant.now());

        listener.onReaction(msg);

        verify(registry).broadcast(eq("reaction"), eq(msg));
    }

    @Test
    void onReaction_scaleUpAction_broadcastsWithNullTarget() {
        ReactionResultMessage msg = new ReactionResultMessage(7L, ReactionAction.SCALE_UP, null, 0L, Instant.now());

        listener.onReaction(msg);

        verify(registry).broadcast(eq("reaction"), eq(msg));
    }

    @Test
    void onReaction_multipleMessages_broadcastsEach() {
        ReactionResultMessage m1 = new ReactionResultMessage(1L, ReactionAction.BLOCK, "1.1.1.1", 300L, Instant.now());
        ReactionResultMessage m2 = new ReactionResultMessage(2L, ReactionAction.RATE_LIMIT, "2.2.2.2", 600L, Instant.now());

        listener.onReaction(m1);
        listener.onReaction(m2);

        verify(registry, times(2)).broadcast(eq("reaction"), any());
    }
}
