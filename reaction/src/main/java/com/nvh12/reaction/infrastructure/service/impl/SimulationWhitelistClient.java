package com.nvh12.reaction.infrastructure.service.impl;

import com.nvh12.reaction.infrastructure.config.SimulationProperties;
import com.nvh12.reaction.service.WhitelistService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class SimulationWhitelistClient implements WhitelistService {

    private static final Duration CACHE_TTL = Duration.ofSeconds(5);

    private final SimulationProperties properties;
    private final RestClient restClient;

    private volatile Set<String> cachedIps = Set.of();
    private volatile Instant cacheExpiresAt = Instant.EPOCH;

    public SimulationWhitelistClient(SimulationProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public boolean isWhitelisted(String ip) {
        return fetchWhitelist().contains(ip);
    }

    private synchronized Set<String> fetchWhitelist() {
        Instant now = Instant.now();
        if (now.isBefore(cacheExpiresAt)) {
            return cachedIps;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = restClient.get()
                    .uri(properties.getUrl() + "/admin/whitelist")
                    .header("X-Admin-Key", properties.getAdminApiKey())
                    .retrieve()
                    .body(Map.class);
            @SuppressWarnings("unchecked")
            List<String> ips = body == null ? List.of() : (List<String>) body.getOrDefault("ips", List.of());
            cachedIps = Set.copyOf(ips);
            cacheExpiresAt = now.plus(CACHE_TTL);
            return cachedIps;
        } catch (HttpClientErrorException.Forbidden e) {
            log.error("Whitelist check rejected by simulation service — ADMIN_API_KEY is misconfigured between reaction and simulation");
            return failOpen(now);
        } catch (Exception e) {
            log.warn("Whitelist check failed — failing open (treating IPs as not whitelisted): {}", e.getMessage());
            return failOpen(now);
        }
    }

    private Set<String> failOpen(Instant now) {
        cachedIps = Set.of();
        cacheExpiresAt = now.plus(CACHE_TTL);
        return cachedIps;
    }
}
