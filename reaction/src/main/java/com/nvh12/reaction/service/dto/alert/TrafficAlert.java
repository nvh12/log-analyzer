package com.nvh12.reaction.service.dto.alert;

import com.nvh12.reaction.service.dto.MethodFlags;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class TrafficAlert extends Alert {

    private final MethodFlags methodFlags;
}
