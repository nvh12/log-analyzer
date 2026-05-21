package com.nvh12.dashboard.infrastructure.mq.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.nvh12.dashboard.domain.ReactionAction;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ReactionResultMessage(
        @JsonProperty("reaction_id")  Long reactionId,
        ReactionAction action,
        String target,
        @JsonProperty("ttl_seconds") Long ttlSeconds,
        Instant ts
) {}
