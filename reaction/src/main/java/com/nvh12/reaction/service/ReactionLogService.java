package com.nvh12.reaction.service;

import com.nvh12.reaction.service.dto.ReactionAction;
import com.nvh12.reaction.service.dto.ReactionInput;

public interface ReactionLogService {
    void save(ReactionInput input, ReactionAction action);
}
