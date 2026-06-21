package com.nvh12.reaction.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "dropped_reactions", schema = "reaction")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DroppedReactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "detection_type", length = 20)
    private String detectionType;

    @Column(name = "source_ip", length = 45)
    private String sourceIp;

    @Column(length = 10)
    private String severity;

    @Column(name = "detected_at")
    private Instant detectedAt;

    @Column(name = "failure_reason", columnDefinition = "text")
    private String failureReason;

    @Column(name = "raw_payload", columnDefinition = "text")
    private String rawPayload;

    @Column(name = "dropped_at", nullable = false)
    private Instant droppedAt;
}
