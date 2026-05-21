package com.nvh12.reaction.service.impl;

import com.nvh12.reaction.service.AlertService;
import com.nvh12.reaction.service.IpBlockService;
import com.nvh12.reaction.service.ReactionLogService;
import com.nvh12.reaction.service.ReactionService;
import com.nvh12.reaction.service.dto.DetectionType;
import com.nvh12.reaction.service.dto.ReactionAction;
import com.nvh12.reaction.service.dto.ReactionInput;
import com.nvh12.reaction.service.dto.WebAttackInput;
import com.nvh12.reaction.service.dto.alert.Alert;
import com.nvh12.reaction.service.dto.alert.WebAttackAlert;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class WebAttackReactionService extends ReactionService {

    private final IpBlockService ipBlockService;

    public WebAttackReactionService(AlertService alertService, ReactionLogService reactionLogService,
                                    IpBlockService ipBlockService) {
        super(DetectionType.WEB_ATTACK, alertService, reactionLogService);
        this.ipBlockService = ipBlockService;
    }

    @Override
    protected Alert buildAlertDto(ReactionInput input) {
        WebAttackInput webAttack = (WebAttackInput) input;
        return WebAttackAlert.builder()
                .detectionType(webAttack.getDetectionType())
                .sourceIp(webAttack.getSourceIp())
                .severity(webAttack.getSeverity())
                .detectedAt(webAttack.getDetectedAt())
                .windowStart(webAttack.getWindowStart())
                .windowEnd(webAttack.getWindowEnd())
                .layerTriggered(webAttack.getLayerTriggered())
                .build();
    }

    @Override
    protected ReactionAction doHandle(ReactionInput input) {
        ipBlockService.block(input.getSourceIp(), input.getSeverity());
        return ReactionAction.BLOCK;
    }
}
