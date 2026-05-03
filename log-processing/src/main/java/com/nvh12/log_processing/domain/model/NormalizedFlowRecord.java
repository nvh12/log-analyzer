package com.nvh12.log_processing.domain.model;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.util.Map;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record NormalizedFlowRecord(
        double timestamp,
        String sourceIp,
        String destIp,
        int sourcePort,
        int destPort,
        Map<String, Double> features
) {
}
