package com.nvh12.dashboard.presentation;

import com.nvh12.dashboard.application.port.SystemMetricsPort;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemController {

    private final SystemMetricsPort systemMetricsPort;

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("queue_depths", systemMetricsPort.queueDepths());
        result.put("redis", systemMetricsPort.redisInfo());
        return result;
    }

    @GetMapping("/config")
    public Map<String, Object> config() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("detection", systemMetricsPort.detectionConfig());
        return result;
    }
}
