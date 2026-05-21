package com.nvh12.dashboard.infrastructure.mq;

import com.nvh12.dashboard.infrastructure.mq.dto.ReactionResultMessage;
import com.nvh12.dashboard.infrastructure.sse.SseEmitterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReactionResultListener {

    private final SseEmitterRegistry registry;

    @RabbitListener(queues = "#{@reactionDashboardQueue.name}")
    public void onReaction(ReactionResultMessage msg) {
        if (msg == null) {
            log.warn("Received null reaction result message");
            return;
        }
        log.debug("Reaction received via MQ: id={} action={} target={}", msg.reactionId(), msg.action(), msg.target());
        registry.broadcast("reaction", msg);
    }
}
