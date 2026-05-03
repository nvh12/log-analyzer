package com.nvh12.log_processing.domain.model;

import lombok.Builder;

import java.time.Instant;

@Builder
public record FailedLogEntry(RawLog rawLog, String failureReason, Instant failedAt, int retryCount) {
    public static FailedLogEntry of(RawLog rawLog, String reason) {
        return FailedLogEntry.builder()
                .rawLog(rawLog)
                .failureReason(reason)
                .failedAt(Instant.now())
                .retryCount(0)
                .build();
    }
}
