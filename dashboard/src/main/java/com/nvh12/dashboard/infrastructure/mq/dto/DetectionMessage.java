package com.nvh12.dashboard.infrastructure.mq.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.nvh12.dashboard.domain.DetectionType;
import com.nvh12.dashboard.domain.NetworkLayer;
import com.nvh12.dashboard.domain.Severity;

import java.time.Instant;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DetectionMessage(
        @JsonProperty("detection_type")  DetectionType detectionType,
        @JsonProperty("network_layer")   NetworkLayer networkLayer,
        Severity severity,
        Boolean anomaly,
        Double confidence,
        @JsonProperty("source_ip")       String sourceIp,
        @JsonProperty("dest_ip")         String destIp,
        @JsonProperty("dest_port")       Integer destPort,
        @JsonProperty("method_flags")    Map<String, Boolean> methodFlags,
        @JsonProperty("layer_triggered") String layerTriggered,
        @JsonProperty("log_timestamp")   Instant logTimestamp,
        @JsonProperty("detected_at")     Instant detectedAt,
        @JsonProperty("window_start")    Instant windowStart,
        @JsonProperty("window_end")      Instant windowEnd
) {}
