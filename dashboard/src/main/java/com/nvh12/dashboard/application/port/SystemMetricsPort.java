package com.nvh12.dashboard.application.port;

import java.util.List;
import java.util.Map;

public interface SystemMetricsPort {
    List<Map<String, Object>> queueDepths();
    Map<String, Object> redisInfo();
    String detectionConfig();
}
