package com.nvh12.reaction.infrastructure.service.impl;

import com.nvh12.reaction.service.AlertService;
import com.nvh12.reaction.service.IpBlockService;
import com.nvh12.reaction.service.RateLimitService;
import com.nvh12.reaction.service.ReactionLogService;
import com.nvh12.reaction.service.WhitelistService;
import com.nvh12.reaction.service.dto.DDoSInput;
import com.nvh12.reaction.service.dto.DetectionType;
import com.nvh12.reaction.service.dto.ReactionInput;
import com.nvh12.reaction.service.dto.alert.Alert;
import com.nvh12.reaction.service.dto.alert.DDoSAlert;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class DDoSReactionService extends EscalatingIpReactionService {

    public DDoSReactionService(AlertService alertService, ReactionLogService reactionLogService,
                               IpBlockService ipBlockService, RateLimitService rateLimitService,
                               RedisTemplate<String, String> redisTemplate, WhitelistService whitelistService) {
        super(DetectionType.DDOS, alertService, reactionLogService,
                ipBlockService, rateLimitService, redisTemplate, whitelistService);
    }

    @Override
    protected String attemptsKeyPrefix() {
        return "ddos:attempts:";
    }

    @Override
    protected Alert buildAlertDto(ReactionInput input) {
        DDoSInput ddos = (DDoSInput) input;
        return DDoSAlert.builder()
                .detectionType(ddos.getDetectionType())
                .sourceIp(ddos.getSourceIp())
                .severity(ddos.getSeverity())
                .detectedAt(ddos.getDetectedAt())
                .windowStart(ddos.getWindowStart())
                .windowEnd(ddos.getWindowEnd())
                .destIp(ddos.getDestIp())
                .destPort(ddos.getDestPort())
                .build();
    }
}
