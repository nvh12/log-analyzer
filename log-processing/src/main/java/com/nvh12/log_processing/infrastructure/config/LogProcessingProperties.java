package com.nvh12.log_processing.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// Defaults are defined here; override any value in application.yaml under log-processing.*
@ConfigurationProperties(prefix = "log-processing")
public record LogProcessingProperties(
        @DefaultValue("10")    int batchSize,
        @DefaultValue("1")     int batchSizeMin,
        @DefaultValue("10000") int batchSizeMax,
        @DefaultValue("40")    int backpressureThreshold,
        @DefaultValue("10000") int mainQueueCapacity,
        @DefaultValue("2000")  int dlqCapacity,
        @DefaultValue("3")     int maxRetries,
        @DefaultValue("30000") long retryDelayMs,
        @DefaultValue("5000")  long retryJitterMs,
        @DefaultValue ThreadPool threadPool,
        @DefaultValue Validation validation
) {
    public LogProcessingProperties {
        if (batchSize < batchSizeMin || batchSize > batchSizeMax) {
            throw new IllegalArgumentException(
                    "log-processing.batch-size must be between " + batchSizeMin +
                            " and " + batchSizeMax + ", got " + batchSize);
        }
    }

    public record ThreadPool(
            @DefaultValue("4")  int corePoolSize,
            @DefaultValue("12") int maxPoolSize,
            @DefaultValue("50") int queueCapacity,
            @DefaultValue("30") int awaitTerminationSeconds
    ) {
    }

    public record Validation(
            @DefaultValue("45")   int maxIpLength,
            @DefaultValue("2048") int maxUrlLength,
            @DefaultValue("512")  int maxUaLength
    ) {
    }
}
