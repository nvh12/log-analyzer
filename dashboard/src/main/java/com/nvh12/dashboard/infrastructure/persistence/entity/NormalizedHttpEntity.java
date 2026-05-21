package com.nvh12.dashboard.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(schema = "log_processing", name = "normalized_http")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NormalizedHttpEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "timestamp")
    private Double timestamp;

    @Column(name = "ip", length = 45)
    private String ip;

    @Column(name = "method", length = 16)
    private String method;

    @Column(name = "url", columnDefinition = "text")
    private String url;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "response_size")
    private Integer responseSize;

    @Column(name = "query_string", columnDefinition = "text")
    private String queryString;

    @Column(name = "user_agent", columnDefinition = "text")
    private String userAgent;

    @Column(name = "referer", columnDefinition = "text")
    private String referer;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;
}
