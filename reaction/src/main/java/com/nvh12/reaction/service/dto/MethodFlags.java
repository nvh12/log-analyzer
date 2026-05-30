package com.nvh12.reaction.service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MethodFlags {

    @JsonProperty("z_score")
    private boolean zScore;
    private boolean iqr;
    private boolean ema;
    private boolean seasonal;

    public String toDisplayString() {
        StringBuilder sb = new StringBuilder();
        if (zScore) sb.append("Z-Score ");
        if (iqr) sb.append("IQR ");
        if (ema) sb.append("EMA ");
        if (seasonal) sb.append("Seasonal");
        return sb.toString().trim();
    }
}
