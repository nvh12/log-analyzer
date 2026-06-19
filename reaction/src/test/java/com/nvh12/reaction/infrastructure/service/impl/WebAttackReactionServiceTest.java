package com.nvh12.reaction.infrastructure.service.impl;

import com.nvh12.reaction.service.AlertService;
import com.nvh12.reaction.service.IpBlockService;
import com.nvh12.reaction.service.ReactionLogService;
import com.nvh12.reaction.service.WhitelistService;
import com.nvh12.reaction.service.dto.DetectionType;
import com.nvh12.reaction.service.dto.ReactionAction;
import com.nvh12.reaction.service.dto.Severity;
import com.nvh12.reaction.service.dto.WebAttackInput;
import com.nvh12.reaction.service.dto.alert.WebAttackAlert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebAttackReactionServiceTest {

    @Mock AlertService alertService;
    @Mock ReactionLogService reactionLogService;
    @Mock IpBlockService ipBlockService;
    @Mock WhitelistService whitelistService;

    WebAttackReactionService service;

    @BeforeEach
    void setUp() {
        service = new WebAttackReactionService(alertService, reactionLogService, ipBlockService, whitelistService);
    }

    @Test
    void handle_blocksSourceIp() {
        WebAttackInput input = new WebAttackInput();
        input.setDetectionType(DetectionType.WEB_ATTACK);
        input.setSourceIp("6.6.6.6");
        input.setSeverity(Severity.HIGH);

        service.handle(input);

        verify(ipBlockService).block("6.6.6.6", Severity.HIGH);
        verify(alertService).enqueue(any(WebAttackAlert.class));
        verify(reactionLogService).save(input, ReactionAction.BLOCK);
    }

    @Test
    void handle_whenWhitelisted_skipsBlockAndAlert() {
        when(whitelistService.isWhitelisted("6.6.6.6")).thenReturn(true);
        WebAttackInput input = new WebAttackInput();
        input.setDetectionType(DetectionType.WEB_ATTACK);
        input.setSourceIp("6.6.6.6");
        input.setSeverity(Severity.HIGH);

        service.handle(input);

        verify(ipBlockService, never()).block(any(), any());
        verify(alertService, never()).enqueue(any());
        verify(reactionLogService).save(input, ReactionAction.WHITELISTED);
    }
}
