package com.nvh12.reaction.service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class BruteForceInput extends ReactionInput {

    private boolean anomaly;
    private double confidence;

    @JsonProperty("dest_ip")
    private String destIp;

    @JsonProperty("dest_port")
    private Integer destPort;
}
