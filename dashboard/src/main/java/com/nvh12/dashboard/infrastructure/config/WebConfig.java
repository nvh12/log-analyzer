package com.nvh12.dashboard.infrastructure.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.net.http.HttpClient;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final DashboardProperties properties;

    @Bean
    public RestClient restClient() {
        // Force HTTP/1.1: the JDK HttpClient's default HTTP/2-upgrade attempt over
        // plaintext is rejected by uvicorn (simulation service) as an invalid request
        // on PUT/POST with a body ("Unsupported upgrade request").
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        return RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory(httpClient))
                .build();
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(properties.corsOrigins().split(","))
                .allowedMethods("GET", "POST")
                .allowedHeaders("*");
    }
}

