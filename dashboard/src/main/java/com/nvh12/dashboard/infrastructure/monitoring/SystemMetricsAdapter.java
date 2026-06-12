package com.nvh12.dashboard.infrastructure.monitoring;

import com.nvh12.dashboard.application.port.SystemMetricsPort;
import com.nvh12.dashboard.infrastructure.config.DashboardProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Properties;

@Slf4j
@Component
@RequiredArgsConstructor
public class SystemMetricsAdapter implements SystemMetricsPort {

    private static final List<String> TRACKED_QUEUES = List.of("log.raw", "log.raw.dlq",
            "log.normalized.http", "log.normalized.flow",
            "detection.results.reaction");

    private final DashboardProperties properties;
    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public List<Map<String, Object>> queueDepths() {
        try {
            RestClient client = RestClient.create();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> queues = client.get()
                    .uri(java.net.URI.create(properties.rabbitmqManagementUrl() + "/api/queues/%2F"))
                    .headers(h -> h.setBasicAuth(properties.rabbitmqManagementUser(),
                            properties.rabbitmqManagementPassword()))
                    .retrieve()
                    .body(List.class);
            if (queues == null) return List.of();
            // "detection.results" and "reaction.results" are exchange names, not queues — omitted.
            // Dashboard's consumer queues are anonymous (UUID names) and not trackable by name.
            return queues.stream()
                    .filter(q -> TRACKED_QUEUES.contains(q.get("name")))
                    .map(q -> Map.<String, Object>of(
                            "name", q.get("name"),
                            "messages", q.getOrDefault("messages", 0)))
                    .toList();
        } catch (Exception e) {
            log.debug("RabbitMQ management API unavailable: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public Map<String, Object> redisInfo() {
        try {
            Properties info = redisTemplate.execute((RedisCallback<Properties>) conn -> conn.serverCommands().info("stats"));
            if (info == null) return Map.of();
            long hits   = parseLong(info.getProperty("keyspace_hits", "0"));
            long misses = parseLong(info.getProperty("keyspace_misses", "0"));
            long total  = hits + misses;
            return Map.of(
                    "keyspace_hits", hits,
                    "keyspace_misses", misses,
                    "hit_rate", total > 0 ? (double) hits / total : 0.0
            );
        } catch (Exception e) {
            log.debug("Redis INFO unavailable: {}", e.getMessage());
            return Map.of();
        }
    }

    @Override
    public String detectionConfig() {
        try {
            RestClient client = RestClient.create();
            return client.get()
                    .uri(properties.detectionMetricsUrl().replace("/metrics", "/config"))
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.debug("Detection config unavailable: {}", e.getMessage());
            return null;
        }
    }

    private long parseLong(String value) {
        try { return Long.parseLong(value.trim()); } catch (NumberFormatException e) { return 0L; }
    }
}
