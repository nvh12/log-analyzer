package com.nvh12.dashboard.application;

import java.time.Instant;
import java.util.Map;

public record FlowLogView(
        Long id,
        Double timestamp,
        String sourceIp,
        String destIp,
        Integer sourcePort,
        Integer destPort,
        Map<String, Double> features,
        Instant processedAt
) {}
