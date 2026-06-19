package com.nvh12.reaction.infrastructure.service.impl;

import com.nvh12.reaction.service.AlertService;
import com.nvh12.reaction.service.IpBlockService;
import com.nvh12.reaction.service.RateLimitService;
import com.nvh12.reaction.service.ReactionLogService;
import com.nvh12.reaction.service.WhitelistService;
import com.nvh12.reaction.service.dto.BruteForceInput;
import com.nvh12.reaction.service.dto.DetectionType;
import com.nvh12.reaction.service.dto.ReactionInput;
import com.nvh12.reaction.service.dto.alert.Alert;
import com.nvh12.reaction.service.dto.alert.BruteForceAlert;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class BruteForceReactionService extends EscalatingIpReactionService {

    public BruteForceReactionService(AlertService alertService, ReactionLogService reactionLogService,
                                     IpBlockService ipBlockService, RateLimitService rateLimitService,
                                     RedisTemplate<String, String> redisTemplate, WhitelistService whitelistService) {
        super(DetectionType.BRUTE_FORCE, alertService, reactionLogService,
                ipBlockService, rateLimitService, redisTemplate, whitelistService);
    }

    @Override
    protected String attemptsKeyPrefix() {
        return BRUTE_FORCE_ATTEMPTS_PREFIX;
    }

    @Override
    protected Alert buildAlertDto(ReactionInput input) {
        BruteForceInput bruteForce = (BruteForceInput) input;
        return BruteForceAlert.builder()
                .detectionType(bruteForce.getDetectionType())
                .sourceIp(bruteForce.getSourceIp())
                .severity(bruteForce.getSeverity())
                .detectedAt(bruteForce.getDetectedAt())
                .windowStart(bruteForce.getWindowStart())
                .windowEnd(bruteForce.getWindowEnd())
                .destIp(bruteForce.getDestIp())
                .destPort(bruteForce.getDestPort())
                .build();
    }
}
