package com.nvh12.dashboard.infrastructure.web;

import com.nvh12.dashboard.application.port.WhitelistPort;
import com.nvh12.dashboard.infrastructure.config.SimulationProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class SimulationWhitelistAdapter implements WhitelistPort {

    private final SimulationProperties properties;
    private final RestClient restClient;

    public SimulationWhitelistAdapter(SimulationProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    @Override
    public List<String> listWhitelistedIps() {
        @SuppressWarnings("unchecked")
        Map<String, Object> body = restClient.get()
                .uri(properties.getUrl() + "/admin/whitelist")
                .header("X-Admin-Key", properties.getAdminApiKey())
                .retrieve()
                .body(Map.class);
        @SuppressWarnings("unchecked")
        List<String> ips = body == null ? List.of() : (List<String>) body.getOrDefault("ips", List.of());
        return ips;
    }

    @Override
    public void replaceWhitelist(List<String> ips) {
        restClient.put()
                .uri(properties.getUrl() + "/admin/whitelist")
                .header("X-Admin-Key", properties.getAdminApiKey())
                .body(ips)
                .retrieve()
                .toBodilessEntity();
        log.info("Replaced whitelist with {} IPs via simulation service", ips.size());
    }
}
