package com.nvh12.dashboard.application;

import com.nvh12.dashboard.domain.DetectionType;
import com.nvh12.dashboard.domain.NetworkLayer;
import com.nvh12.dashboard.domain.Severity;

import java.time.Instant;
import java.util.Map;

public record DetectionDetailView(
        Long id,
        DetectionType detectionType,
        Severity severity,
        Boolean anomaly,
        Double confidence,
        NetworkLayer networkLayer,
        String sourceIp,
        String destIp,
        Integer destPort,
        Instant logTimestamp,
        Instant windowStart,
        Instant windowEnd,
        Instant detectedAt,
        Map<String, Object> payload
) {}
