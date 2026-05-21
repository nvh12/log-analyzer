package com.nvh12.dashboard.application;

import com.nvh12.dashboard.domain.DetectionType;
import com.nvh12.dashboard.domain.NetworkLayer;
import com.nvh12.dashboard.domain.ReactionAction;
import com.nvh12.dashboard.domain.Severity;

import java.time.Instant;

public record ReactionSummaryView(
        Long id,
        DetectionType detectionType,
        String sourceIp,
        Severity severity,
        ReactionAction action,
        NetworkLayer networkLayer,
        Instant detectedAt,
        Instant reactedAt
) {}
