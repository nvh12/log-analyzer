package com.nvh12.log_processing.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "normalized_flow")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NormalizedFlowEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "timestamp", nullable = false)
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
    @Column(name = "features", nullable = false, columnDefinition = "jsonb")
    private Map<String, Double> features;

    @ColumnDefault("now()")
    @Column(name = "processed_at", nullable = false, insertable = false, updatable = false)
    private Instant processedAt;
}
