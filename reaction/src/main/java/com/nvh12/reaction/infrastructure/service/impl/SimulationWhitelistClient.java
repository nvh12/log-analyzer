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

    private record WhitelistCache(Set<String> ips, Instant expiresAt) {}

    private static final WhitelistCache EMPTY_EXPIRED = new WhitelistCache(Set.of(), Instant.EPOCH);

    private final SimulationProperties properties;
    private final RestClient restClient;

    private volatile WhitelistCache cache = EMPTY_EXPIRED;

    public SimulationWhitelistClient(SimulationProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public boolean isWhitelisted(String ip) {
        return fetchWhitelist().contains(ip);
    }

    private Set<String> fetchWhitelist() {
        Instant now = Instant.now();
        WhitelistCache snapshot = cache;
        if (now.isBefore(snapshot.expiresAt())) {
            return snapshot.ips();
        }
        synchronized (this) {
            // Re-check: another thread may have already refreshed while we waited for the lock.
            snapshot = cache;
            if (now.isBefore(snapshot.expiresAt())) {
                return snapshot.ips();
            }
            return refresh(now);
        }
    }

    private Set<String> refresh(Instant now) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = restClient.get()
                    .uri(properties.getUrl() + "/admin/whitelist")
                    .header("X-Admin-Key", properties.getAdminApiKey())
                    .retrieve()
                    .body(Map.class);
            @SuppressWarnings("unchecked")
            List<String> ips = body == null ? List.of() : (List<String>) body.getOrDefault("ips", List.of());
            Set<String> fetched = Set.copyOf(ips);
            cache = new WhitelistCache(fetched, now.plus(CACHE_TTL));
            return fetched;
        } catch (HttpClientErrorException.Forbidden e) {
            log.error("Whitelist check rejected by simulation service — ADMIN_API_KEY is misconfigured between reaction and simulation");
            return failOpen(now);
        } catch (Exception e) {
            log.warn("Whitelist check failed — failing open (treating IPs as not whitelisted): {}", e.getMessage());
            return failOpen(now);
        }
    }

    private Set<String> failOpen(Instant now) {
        cache = new WhitelistCache(Set.of(), now.plus(CACHE_TTL));
        return Set.of();
    }
}
