package com.nvh12.log_processing.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "log-processing")
public record LogProcessingProperties(
        int batchSize,
        int batchSizeMin,
        int batchSizeMax,
        int backpressureThreshold,
        int mainQueueCapacity,
        int dlqCapacity,
        int maxRetries,
        long retryDelayMs,
        long retryJitterMs,
        ThreadPool threadPool
) {
    public LogProcessingProperties {
        if (batchSize < batchSizeMin || batchSize > batchSizeMax) {
            throw new IllegalArgumentException(
                    "log-processing.batch-size must be between " + batchSizeMin +
                            " and " + batchSizeMax + ", got " + batchSize);
        }
    }

    public record ThreadPool(
            int corePoolSize,
            int maxPoolSize,
            int queueCapacity,
            int awaitTerminationSeconds
    ) {
    }
}
