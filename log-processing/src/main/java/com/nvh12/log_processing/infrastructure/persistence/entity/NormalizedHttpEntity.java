package com.nvh12.log_processing.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "normalized_http")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NormalizedHttpEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "timestamp", nullable = false)
    private Double timestamp;

    @Column(name = "ip", length = 64)
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

    @Column(name = "body", columnDefinition = "text")
    private String body;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "headers", columnDefinition = "jsonb")
    private Map<String, String> headers;

    @Column(name = "user_agent", columnDefinition = "text")
    private String userAgent;

    @Column(name = "referer", columnDefinition = "text")
    private String referer;

    @ColumnDefault("now()")
    @Column(name = "processed_at", nullable = false, insertable = false, updatable = false)
    private Instant processedAt;
}
