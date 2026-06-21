package com.nvh12.reaction.service;

import java.time.Instant;

public interface DroppedReactionService {
    void record(String detectionType, String sourceIp, String severity, Instant detectedAt,
                String failureReason, String rawPayload);
}
