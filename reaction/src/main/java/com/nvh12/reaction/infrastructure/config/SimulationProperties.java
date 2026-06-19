package com.nvh12.reaction.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "simulation")
public class SimulationProperties {

    private String url = "http://localhost:8001";
    private String adminApiKey;
}
