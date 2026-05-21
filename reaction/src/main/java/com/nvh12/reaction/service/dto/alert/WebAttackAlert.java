package com.nvh12.reaction.service.dto.alert;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class WebAttackAlert extends Alert {

    private final String layerTriggered;
}
