package com.nvh12.reaction.service.dto.alert;

import com.nvh12.reaction.service.dto.DetectionType;
import com.nvh12.reaction.service.dto.Severity;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Getter
@SuperBuilder
public abstract class Alert {

    private final DetectionType detectionType;
    private final String sourceIp;
    private final Severity severity;
    private final Instant detectedAt;
    private final Instant windowStart;
    private final Instant windowEnd;
}
