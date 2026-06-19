package com.nvh12.dashboard.infrastructure.persistence.entity;

import com.nvh12.dashboard.domain.DetectionType;
import com.nvh12.dashboard.domain.NetworkLayer;
import com.nvh12.dashboard.domain.Severity;
import com.nvh12.dashboard.infrastructure.persistence.mapper.JsonBooleanMapConverter;
import jakarta.persistence.*;
import org.hibernate.annotations.ColumnTransformer;
import lombok.*;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(schema = "analysis", name = "detection_results")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DetectionResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "detection_type", nullable = false, length = 20)
    private DetectionType detectionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Severity severity;

    @Column(nullable = false)
    private Boolean anomaly;

    @Column(name = "confidence")
    private Double confidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "network_layer", nullable = false, length = 5)
    private NetworkLayer networkLayer;

    @Column(name = "source_ip", length = 45)
    private String sourceIp;

    @Column(name = "dest_ip", length = 45)
    private String destIp;

    @Column(name = "dest_port")
    private Integer destPort;

    @Convert(converter = JsonBooleanMapConverter.class)
    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "method_flags", columnDefinition = "jsonb")
    private Map<String, Boolean> methodFlags;

    @Column(name = "layer_triggered", length = 32)
    private String layerTriggered;

    @Column(name = "log_timestamp")
    private Instant logTimestamp;

    @Column(name = "window_start")
    private Instant windowStart;

    @Column(name = "window_end")
    private Instant windowEnd;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;
}
