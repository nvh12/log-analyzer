package com.nvh12.reaction.service.dto.alert;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class BruteForceAlert extends Alert {

    private final String destIp;
    private final Integer destPort;
}
