package com.nvh12.reaction.service.impl;

import com.nvh12.reaction.infrastructure.mq.ReactionResultPublisher;
import com.nvh12.reaction.infrastructure.persistence.entity.ReactionLogEntity;
import com.nvh12.reaction.infrastructure.persistence.repository.ReactionLogRepository;
import com.nvh12.reaction.service.ReactionLogService;
import com.nvh12.reaction.service.dto.NetworkLayer;
import com.nvh12.reaction.service.dto.ReactionAction;
import com.nvh12.reaction.service.dto.ReactionInput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class JpaReactionLogService implements ReactionLogService {

    private final ReactionLogRepository repository;
    private final ReactionResultPublisher publisher;

    @Override
    public void save(ReactionInput input, ReactionAction action) {
        try {
            Instant reactedAt = Instant.now();
            ReactionLogEntity saved = repository.save(ReactionLogEntity.builder()
                    .detectionType(input.getDetectionType())
                    .sourceIp(input.getSourceIp())
                    .severity(input.getSeverity())
                    .action(action)
                    .networkLayer(input.getNetworkLayer() != null
                            ? input.getNetworkLayer()
                            : NetworkLayer.from(input.getDetectionType()))
                    .detectedAt(input.getDetectedAt())
                    .windowStart(input.getWindowStart())
                    .windowEnd(input.getWindowEnd())
                    .reactedAt(reactedAt)
                    .build());
            publisher.publish(saved.getId(), action, input.getSourceIp(), input.getSeverity(), reactedAt);
        } catch (Exception e) {
            log.error("Failed to persist reaction log [type={} ip={} action={}]: {}",
                    input.getDetectionType(), input.getSourceIp(), action, e.getMessage());
        }
    }
}
