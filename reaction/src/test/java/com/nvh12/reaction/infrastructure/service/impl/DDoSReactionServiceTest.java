package com.nvh12.reaction.infrastructure.service.impl;

import com.nvh12.reaction.service.dto.DDoSInput;
import com.nvh12.reaction.service.dto.DetectionType;
import com.nvh12.reaction.service.dto.ReactionInput;
import com.nvh12.reaction.service.dto.Severity;
import com.nvh12.reaction.service.dto.alert.Alert;
import com.nvh12.reaction.service.dto.alert.DDoSAlert;
import org.junit.jupiter.api.BeforeEach;

class DDoSReactionServiceTest extends EscalatingIpReactionServiceTestBase {

    DDoSReactionService service;

    @BeforeEach
    void setUp() {
        service = new DDoSReactionService(alertService, reactionLogService, ipBlockService, rateLimitService, redisTemplate);
    }

    @Override
    EscalatingIpReactionService service() {
        return service;
    }

    @Override
    ReactionInput input(Severity severity) {
        DDoSInput input = new DDoSInput();
        input.setDetectionType(DetectionType.DDOS);
        input.setSourceIp(IP);
        input.setSeverity(severity);
        input.setDestIp("10.0.0.1");
        input.setDestPort(80);
        return input;
    }

    @Override
    String attemptsKeyPrefix() {
        return "ddos:attempts:";
    }

    @Override
    Class<? extends Alert> alertType() {
        return DDoSAlert.class;
    }
}
