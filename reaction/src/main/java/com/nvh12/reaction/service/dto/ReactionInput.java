package com.nvh12.reaction.service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "detection_type", include = JsonTypeInfo.As.EXISTING_PROPERTY, visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = TrafficInput.class,    name = "TRAFFIC"),
        @JsonSubTypes.Type(value = DDoSInput.class,       name = "DDOS"),
        @JsonSubTypes.Type(value = WebAttackInput.class,  name = "WEB_ATTACK"),
        @JsonSubTypes.Type(value = BruteForceInput.class, name = "BRUTE_FORCE")
})
public abstract class ReactionInput {

    @JsonProperty("detection_type")
    private DetectionType detectionType;

    @JsonProperty("network_layer")
    private NetworkLayer networkLayer;

    @JsonProperty("log_timestamp")
    private Instant logTimestamp;

    @JsonProperty("detected_at")
    private Instant detectedAt;

    @JsonProperty("source_ip")
    private String sourceIp;

    private Severity severity;

    @JsonProperty("window_start")
    private Instant windowStart;

    @JsonProperty("window_end")
    private Instant windowEnd;
}
