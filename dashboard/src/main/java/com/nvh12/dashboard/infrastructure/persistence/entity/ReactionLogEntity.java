package com.nvh12.dashboard.infrastructure.persistence.entity;

import com.nvh12.dashboard.domain.DetectionType;
import com.nvh12.dashboard.domain.NetworkLayer;
import com.nvh12.dashboard.domain.ReactionAction;
import com.nvh12.dashboard.domain.Severity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(schema = "reaction", name = "reaction_logs")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReactionLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "detection_type", nullable = false, length = 20)
    private DetectionType detectionType;

    @Column(name = "source_ip", length = 45)
    private String sourceIp;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private ReactionAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "network_layer", nullable = false, length = 10)
    private NetworkLayer networkLayer;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @Column(name = "window_start")
    private Instant windowStart;

    @Column(name = "window_end")
    private Instant windowEnd;

    @Column(name = "reacted_at", nullable = false)
    private Instant reactedAt;
}
