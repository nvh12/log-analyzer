package com.nvh12.reaction.service;

import com.nvh12.reaction.service.dto.ReactionAction;
import com.nvh12.reaction.service.dto.Severity;

import java.time.Instant;

public interface ReactionEventPort {
    void publish(Long reactionId, ReactionAction action, String target, Severity severity, Instant ts);
}
