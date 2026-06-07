package com.nvh12.log_processing.infrastructure.config;

import com.nvh12.log_processing.application.DlqRetryScheduler;
import com.nvh12.log_processing.domain.model.ValidationLimits;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ApplicationBeans {

    @Bean
    ValidationLimits validationLimits(LogProcessingProperties p) {
        return new ValidationLimits(
                p.validation().maxIpLength(),
                p.validation().maxUrlLength(),
                p.validation().maxUaLength()
        );
    }

    @Bean
    DlqRetryScheduler.Config dlqRetryConfig(LogProcessingProperties p) {
        return new DlqRetryScheduler.Config(
                p.maxRetries(),
                p.retryBatchSize(),
                p.retryDelayMs(),
                p.retryJitterMs()
        );
    }
}
