package com.nvh12.log_processing.infrastructure.persistence.entity;

import com.nvh12.log_processing.infrastructure.persistence.mapper.JsonDoubleMapConverter;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.ColumnTransformer;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "normalized_flow", schema = "log_processing")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NormalizedFlowEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_log_id", length = 64, unique = true)
    private String sourceLogId;

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

    @Convert(converter = JsonDoubleMapConverter.class)
    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "features", nullable = false, columnDefinition = "jsonb")
    private Map<String, Double> features;

    @ColumnDefault("now()")
    @Column(name = "processed_at", nullable = false, insertable = false, updatable = false)
    private Instant processedAt;
}
