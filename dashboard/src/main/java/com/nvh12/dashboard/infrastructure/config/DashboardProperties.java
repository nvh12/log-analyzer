package com.nvh12.dashboard.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "dashboard")
public record DashboardProperties(
        @DefaultValue("http://localhost:3000") String corsOrigins,
        @DefaultValue("http://localhost:15672") String rabbitmqManagementUrl,
        @DefaultValue("guest") String rabbitmqManagementUser,
        @DefaultValue("guest") String rabbitmqManagementPassword,
        @DefaultValue("http://localhost:8000/metrics") String detectionMetricsUrl
) {}
