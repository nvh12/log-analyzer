package com.nvh12.dashboard.presentation;

import com.nvh12.dashboard.config.DashboardProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemController {

    private final DashboardProperties properties;
    private final RedisTemplate<String, String> redisTemplate;

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("queue_depths", fetchQueueDepths());
        result.put("redis", fetchRedisInfo());
        return result;
    }

    @GetMapping("/config")
    public Map<String, Object> config() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            RestClient client = RestClient.create();
            String raw = client.get()
                    .uri(properties.detectionMetricsUrl().replace("/metrics", "/config"))
                    .retrieve()
                    .body(String.class);
            result.put("detection", raw);
        } catch (Exception e) {
            log.debug("Detection config unavailable: {}", e.getMessage());
            result.put("detection", null);
        }
        return result;
    }

    private List<Map<String, Object>> fetchQueueDepths() {
        try {
            RestClient client = RestClient.create();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> queues = client.get()
                    .uri(properties.rabbitmqManagementUrl() + "/api/queues/%2F")
                    .headers(h -> h.setBasicAuth(properties.rabbitmqManagementUser(),
                            properties.rabbitmqManagementPassword()))
                    .retrieve()
                    .body(List.class);
            if (queues == null) return List.of();
            List<String> tracked = List.of("log.raw", "log.normalized.http", "log.normalized.flow",
                    "detection.results.reaction", "detection.results", "reaction.results");
            return queues.stream()
                    .filter(q -> tracked.contains(q.get("name")))
                    .map(q -> Map.<String, Object>of(
                            "name", q.get("name"),
                            "messages", q.getOrDefault("messages", 0)))
                    .toList();
        } catch (Exception e) {
            log.debug("RabbitMQ management API unavailable: {}", e.getMessage());
            return List.of();
        }
    }

    private Map<String, Object> fetchRedisInfo() {
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

    private long parseLong(String value) {
        try { return Long.parseLong(value.trim()); } catch (NumberFormatException e) { return 0L; }
    }
}
