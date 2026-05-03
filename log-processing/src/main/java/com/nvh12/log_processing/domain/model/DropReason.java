package com.nvh12.log_processing.domain.model;

public enum DropReason {
    DLQ_OVERFLOW,
    RETRY_EXHAUSTED,
    DEAD_LETTERED
}
