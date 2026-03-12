package com.nvh12;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringBootApplication
public class LogAnalyzerApplication {
    public static void main(String[] args) {
        SpringApplication.run(LogAnalyzerApplication.class, args);
        log.info("LogAnalyzerApplication has started successfully.");
    }
}
