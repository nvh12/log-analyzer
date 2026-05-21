package com.nvh12.reaction.service.impl;

import com.nvh12.reaction.service.AlertService;
import com.nvh12.reaction.service.IpBlockService;
import com.nvh12.reaction.service.ReactionLogService;
import com.nvh12.reaction.service.dto.DDoSInput;
import com.nvh12.reaction.service.dto.DetectionType;
import com.nvh12.reaction.service.dto.ReactionAction;
import com.nvh12.reaction.service.dto.Severity;
import com.nvh12.reaction.service.dto.alert.DDoSAlert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DDoSReactionServiceTest {

    @Mock AlertService alertService;
    @Mock ReactionLogService reactionLogService;
    @Mock IpBlockService ipBlockService;

    DDoSReactionService service;

    @BeforeEach
    void setUp() {
        service = new DDoSReactionService(alertService, reactionLogService, ipBlockService);
    }

    @Test
    void handle_blocksSourceIp() {
        DDoSInput input = new DDoSInput();
        input.setDetectionType(DetectionType.DDOS);
        input.setSourceIp("5.5.5.5");
        input.setSeverity(Severity.CRITICAL);

        service.handle(input);

        verify(ipBlockService).block("5.5.5.5", Severity.CRITICAL);
        verify(alertService).alert(any(DDoSAlert.class));
        verify(reactionLogService).save(input, ReactionAction.BLOCK);
    }
}
