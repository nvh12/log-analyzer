package com.nvh12.log_processing.domain.model;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.util.Map;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record NormalizedLog(
        double timestamp,
        String ip,
        HttpMethod method,
        String url,
        int statusCode,
        int responseSize,
        String queryString,
        Map<String, String> headers,
        String userAgent,
        String referer
) {
}
