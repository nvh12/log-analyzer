package com.nvh12.reaction.service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TrafficInput extends ReactionInput {

    private boolean anomaly;
    private double confidence;

    @JsonProperty("method_flags")
    private MethodFlags methodFlags;
}
