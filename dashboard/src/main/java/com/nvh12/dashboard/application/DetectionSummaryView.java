package com.nvh12.dashboard.application;

import com.nvh12.dashboard.domain.DetectionType;
import com.nvh12.dashboard.domain.Severity;

import java.time.Instant;

public record DetectionSummaryView(
        Long id,
        DetectionType detectionType,
        Severity severity,
        Boolean anomaly,
        Double confidence,
        String sourceIp,
        Instant detectedAt
) {}
