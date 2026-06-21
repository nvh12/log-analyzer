package com.nvh12.reaction.infrastructure.service.impl;

import com.nvh12.reaction.infrastructure.persistence.entity.DroppedReactionEntity;
import com.nvh12.reaction.infrastructure.persistence.repository.DroppedReactionRepository;
import com.nvh12.reaction.service.DroppedReactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class JpaDroppedReactionService implements DroppedReactionService {

    private final DroppedReactionRepository repository;

    @Override
    public void record(String detectionType, String sourceIp, String severity, Instant detectedAt,
                        String failureReason, String rawPayload) {
        repository.save(DroppedReactionEntity.builder()
                .detectionType(detectionType)
                .sourceIp(sourceIp)
                .severity(severity)
                .detectedAt(detectedAt)
                .failureReason(failureReason)
                .rawPayload(rawPayload)
                .droppedAt(Instant.now())
                .build());
    }
}
