package com.nvh12.log_processing.domain.model;

import javax.print.attribute.standard.Severity;
import java.time.Instant;
import java.util.Map;

public class NormalizedLog {
    private String id;
    private Instant timestamp;

    private String serviceName;
    private Severity severity;
    private String eventType;

    private String userId;
    private String sourceIp;
    private String traceId;
    private String sessionId;

    private Map<String, Object> attributes;
}
