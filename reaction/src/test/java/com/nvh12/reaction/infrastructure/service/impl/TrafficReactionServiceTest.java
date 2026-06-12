package com.nvh12.reaction.infrastructure.service.impl;

import com.nvh12.reaction.service.AlertService;
import com.nvh12.reaction.service.ReactionLogService;
import com.nvh12.reaction.service.ScaleService;
import com.nvh12.reaction.service.dto.DetectionType;
import com.nvh12.reaction.service.dto.ReactionAction;
import com.nvh12.reaction.service.dto.Severity;
import com.nvh12.reaction.service.dto.TrafficInput;
import com.nvh12.reaction.service.dto.alert.TrafficAlert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TrafficReactionServiceTest {

    @Mock AlertService alertService;
    @Mock ReactionLogService reactionLogService;
    @Mock ScaleService scaleService;

    TrafficReactionService service;

    @BeforeEach
    void setUp() {
        service = new TrafficReactionService(alertService, reactionLogService, scaleService);
    }

    @Test
    void handle_requestsScaleWithDetectionTypeAndSeverity() {
        TrafficInput input = new TrafficInput();
        input.setDetectionType(DetectionType.TRAFFIC);
        input.setSourceIp("1.2.3.4");
        input.setSeverity(Severity.HIGH);

        service.handle(input);

        verify(scaleService).requestScale(DetectionType.TRAFFIC, Severity.HIGH);
        verify(alertService).enqueue(any(TrafficAlert.class));
        verify(reactionLogService).save(input, ReactionAction.SCALE_UP);
    }
}
