package com.nvh12.reaction.infrastructure.service.impl;

import com.nvh12.reaction.service.AlertService;
import com.nvh12.reaction.service.ReactionLogService;
import com.nvh12.reaction.service.ReactionService;
import com.nvh12.reaction.service.ScaleService;
import com.nvh12.reaction.service.dto.DetectionType;
import com.nvh12.reaction.service.dto.ReactionAction;
import com.nvh12.reaction.service.dto.ReactionInput;
import com.nvh12.reaction.service.dto.TrafficInput;
import com.nvh12.reaction.service.dto.alert.Alert;
import com.nvh12.reaction.service.dto.alert.TrafficAlert;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class TrafficReactionService extends ReactionService {

    private final ScaleService scaleService;

    public TrafficReactionService(AlertService alertService, ReactionLogService reactionLogService,
                                  ScaleService scaleService) {
        super(DetectionType.TRAFFIC, alertService, reactionLogService);
        this.scaleService = scaleService;
    }

    @Override
    protected Alert buildAlertDto(ReactionInput input) {
        TrafficInput traffic = (TrafficInput) input;
        return TrafficAlert.builder()
                .detectionType(traffic.getDetectionType())
                .sourceIp(traffic.getSourceIp())
                .severity(traffic.getSeverity())
                .detectedAt(traffic.getDetectedAt())
                .windowStart(traffic.getWindowStart())
                .windowEnd(traffic.getWindowEnd())
                .methodFlags(traffic.getMethodFlags())
                .build();
    }

    @Override
    protected ReactionAction doHandle(ReactionInput input) {
        TrafficInput traffic = (TrafficInput) input;
        scaleService.requestScale(traffic.getDetectionType(), traffic.getSeverity());
        return ReactionAction.SCALE_UP;
    }
}
