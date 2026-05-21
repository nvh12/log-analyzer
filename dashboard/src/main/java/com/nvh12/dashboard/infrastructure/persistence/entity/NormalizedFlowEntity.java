package com.nvh12.dashboard.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(schema = "log_processing", name = "normalized_flow")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NormalizedFlowEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "timestamp")
    private Double timestamp;

    @Column(name = "source_ip", length = 64)
    private String sourceIp;

    @Column(name = "dest_ip", length = 64)
    private String destIp;

    @Column(name = "source_port")
    private Integer sourcePort;

    @Column(name = "dest_port")
    private Integer destPort;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "features", columnDefinition = "jsonb")
    private Map<String, Double> features;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;
}
