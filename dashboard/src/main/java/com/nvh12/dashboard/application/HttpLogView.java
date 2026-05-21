package com.nvh12.dashboard.application;

import java.time.Instant;

public record HttpLogView(
        Long id,
        Double timestamp,
        String ip,
        String method,
        String url,
        Integer statusCode,
        Integer responseSize,
        String queryString,
        String userAgent,
        String referer,
        Instant processedAt
) {}
