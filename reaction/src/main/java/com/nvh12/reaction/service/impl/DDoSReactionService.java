package com.nvh12.reaction.service.impl;

import com.nvh12.reaction.service.AlertService;
import com.nvh12.reaction.service.IpBlockService;
import com.nvh12.reaction.service.ReactionLogService;
import com.nvh12.reaction.service.ReactionService;
import com.nvh12.reaction.service.dto.DDoSInput;
import com.nvh12.reaction.service.dto.DetectionType;
import com.nvh12.reaction.service.dto.ReactionAction;
import com.nvh12.reaction.service.dto.ReactionInput;
import com.nvh12.reaction.service.dto.alert.Alert;
import com.nvh12.reaction.service.dto.alert.DDoSAlert;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class DDoSReactionService extends ReactionService {

    private final IpBlockService ipBlockService;

    public DDoSReactionService(AlertService alertService, ReactionLogService reactionLogService,
                               IpBlockService ipBlockService) {
        super(DetectionType.DDOS, alertService, reactionLogService);
        this.ipBlockService = ipBlockService;
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

    @Override
    protected ReactionAction doHandle(ReactionInput input) {
        ipBlockService.block(input.getSourceIp(), input.getSeverity());
        return ReactionAction.BLOCK;
    }
}
