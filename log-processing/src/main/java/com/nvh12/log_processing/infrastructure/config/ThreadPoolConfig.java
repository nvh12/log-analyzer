package com.nvh12.log_processing.infrastructure.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class ThreadPoolConfig {

    @Bean
    @Qualifier("logWorkerExecutor")
    public ThreadPoolTaskExecutor logWorkerExecutor(LogProcessingProperties properties) {
        LogProcessingProperties.ThreadPool tp = properties.threadPool();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(tp.corePoolSize());
        executor.setMaxPoolSize(tp.maxPoolSize());
        executor.setQueueCapacity(tp.queueCapacity());
        executor.setThreadNamePrefix("LogWorker-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(tp.awaitTerminationSeconds());
        return executor;
    }
}
