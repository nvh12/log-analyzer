package com.nvh12.log_processing.domain.model;

public sealed interface ProcessingResult
        permits ProcessingResult.Http, ProcessingResult.Flow {

    record Http(NormalizedLog log) implements ProcessingResult {
    }

    record Flow(NormalizedFlowRecord record) implements ProcessingResult {
    }
}
