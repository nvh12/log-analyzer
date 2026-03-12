package com.nvh12.logprocessing.domain.model;

import java.time.Instant;
import lombok.Builder;

// Lombok
@Builder
public record FailedLogEntry(RawLog rawLog, String failureReason, Instant failedAt) {
  public static FailedLogEntry of(RawLog rawLog, String reason) {
    return FailedLogEntry.builder()
        .rawLog(rawLog)
        .failureReason(reason)
        .failedAt(Instant.now())
        .build();
  }
}
