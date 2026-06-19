package com.nvh12.reaction.infrastructure.service.impl;

import com.nvh12.reaction.service.dto.BruteForceInput;
import com.nvh12.reaction.service.dto.DetectionType;
import com.nvh12.reaction.service.dto.ReactionInput;
import com.nvh12.reaction.service.dto.Severity;
import com.nvh12.reaction.service.dto.alert.Alert;
import com.nvh12.reaction.service.dto.alert.BruteForceAlert;
import org.junit.jupiter.api.BeforeEach;

class BruteForceReactionServiceTest extends EscalatingIpReactionServiceTestBase {

    BruteForceReactionService service;

    @BeforeEach
    void setUp() {
        service = new BruteForceReactionService(alertService, reactionLogService, ipBlockService, rateLimitService, redisTemplate, whitelistService);
    }

    @Override
    EscalatingIpReactionService service() {
        return service;
    }

    @Override
    ReactionInput input(Severity severity) {
        BruteForceInput input = new BruteForceInput();
        input.setDetectionType(DetectionType.BRUTE_FORCE);
        input.setSourceIp(IP);
        input.setSeverity(severity);
        input.setDestIp("192.168.1.1");
        input.setDestPort(22);
        return input;
    }

    @Override
    String attemptsKeyPrefix() {
        return "brute:attempts:";
    }

    @Override
    Class<? extends Alert> alertType() {
        return BruteForceAlert.class;
    }
}
