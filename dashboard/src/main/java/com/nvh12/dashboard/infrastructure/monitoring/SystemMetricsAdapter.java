package com.nvh12.dashboard.infrastructure.monitoring;

import com.nvh12.dashboard.application.port.SystemMetricsPort;
import com.nvh12.dashboard.infrastructure.config.DashboardProperties;
import com.nvh12.dashboard.infrastructure.config.SimulationProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class SystemMetricsAdapter implements SystemMetricsPort {

    private static final List<String> TRACKED_QUEUES = List.of("log.raw", "log.raw.dlq",
            "log.normalized.http", "log.normalized.flow",
            "detection.results.reaction");
    private static final String CURRENT_WORKERS_KEY = "scale:current_workers";
    private static final String REPLICAS_KEY = "scale:replicas";

    private final DashboardProperties properties;
    private final SimulationProperties simulationProperties;
    private final RedisTemplate<String, String> redisTemplate;
    private final RestClient restClient;

    public SystemMetricsAdapter(DashboardProperties properties, SimulationProperties simulationProperties,
                                 RedisTemplate<String, String> redisTemplate, RestClient restClient) {
        this.properties = properties;
        this.simulationProperties = simulationProperties;
        this.redisTemplate = redisTemplate;
        this.restClient = restClient;
    }

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
    public Map<String, Object> workerScale() {
        Map<String, Object> workers = simulationWorkerConfig();
        int min = ((Number) workers.getOrDefault("min", 0)).intValue();
        int max = ((Number) workers.getOrDefault("max", 0)).intValue();
        int defaultWorkers = ((Number) workers.getOrDefault("default", 0)).intValue();

        Integer current = readRedisInt(CURRENT_WORKERS_KEY);
        Integer target = readRedisInt(REPLICAS_KEY);
        int currentValue = current != null ? current : defaultWorkers;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("current", currentValue);
        result.put("target", target != null ? target : currentValue);
        result.put("min", min);
        result.put("max", max);
        result.put("available", Math.max(0, max - currentValue));
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> simulationWorkerConfig() {
        try {
            Map<String, Object> config = restClient.get()
                    .uri(simulationProperties.getUrl() + "/config")
                    .retrieve()
                    .body(Map.class);
            return config == null ? Map.of() : (Map<String, Object>) config.getOrDefault("workers", Map.of());
        } catch (Exception e) {
            log.debug("Simulation worker config unavailable: {}", e.getMessage());
            return Map.of();
        }
    }

    private Integer readRedisInt(String key) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            return value != null ? Integer.parseInt(value) : null;
        } catch (Exception e) {
            log.debug("Redis key {} unavailable: {}", key, e.getMessage());
            return null;
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
}
