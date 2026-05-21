package com.nvh12.log_processing.infrastructure.persistence.entity;

import com.nvh12.log_processing.domain.model.DropReason;
import com.nvh12.log_processing.domain.model.LogSource;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "drop_audit", schema = "log_processing", indexes = {
        @Index(name = "idx_drop_audit_log_id", columnList = "log_id"),
        @Index(name = "idx_drop_audit_dropped_at", columnList = "dropped_at"),
        @Index(name = "idx_drop_audit_drop_reason", columnList = "drop_reason")
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DropAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "log_id")
    private String logId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 64)
    private LogSource source;

    @Column(name = "raw_message", columnDefinition = "text")
    private String rawMessage;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "drop_reason", nullable = false, length = 32)
    private DropReason dropReason;

    @Column(name = "failure_reason", columnDefinition = "text")
    private String failureReason;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "dropped_at", nullable = false)
    private Instant droppedAt;
}
