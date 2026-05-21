package com.nvh12.reaction.service.dto.alert;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class DDoSAlert extends Alert {

    private final String destIp;
    private final Integer destPort;
}
