package com.nvh12.reaction.service;

import com.nvh12.reaction.service.dto.DetectionType;
import com.nvh12.reaction.service.dto.ReactionAction;
import com.nvh12.reaction.service.dto.ReactionInput;
import com.nvh12.reaction.service.dto.alert.Alert;

public abstract class ReactionService {

    private final DetectionType type;

    protected final AlertService alertService;
    protected final ReactionLogService reactionLogService;

    protected ReactionService(DetectionType type, AlertService alertService, ReactionLogService reactionLogService) {
        this.type = type;
        this.alertService = alertService;
        this.reactionLogService = reactionLogService;
    }

    public DetectionType getType() {
        return type;
    }

    public final void handle(ReactionInput input) {
        ReactionAction action = doHandle(input);
        reactionLogService.save(input, action);
        alertService.enqueue(buildAlertDto(input));
    }

    protected abstract Alert buildAlertDto(ReactionInput input);

    protected abstract ReactionAction doHandle(ReactionInput input);
}
