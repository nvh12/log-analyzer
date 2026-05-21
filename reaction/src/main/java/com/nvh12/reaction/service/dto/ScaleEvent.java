package com.nvh12.reaction.service.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class ScaleEvent {

    private final DetectionType detectionType;
    private final Severity severity;
    private final Instant triggeredAt;
}
