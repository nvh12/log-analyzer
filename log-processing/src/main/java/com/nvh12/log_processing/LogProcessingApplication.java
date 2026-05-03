package com.nvh12.log_processing;

import com.nvh12.log_processing.infrastructure.config.LogProcessingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(LogProcessingProperties.class)
public class LogProcessingApplication {

    public static void main(String[] args) {
        SpringApplication.run(LogProcessingApplication.class, args);
    }

}
