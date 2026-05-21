package com.nvh12.log_processing.domain.model;

public enum DropReason {
    DLQ_OVERFLOW,
    DLQ_SAVE_FAILED,
    RETRY_EXHAUSTED,
    DEAD_LETTERED
}
