package com.nvh12.reaction.service;

import com.nvh12.reaction.service.dto.Severity;

public interface RateLimitService {

    void limit(String ip, Severity severity);

    boolean isLimited(String ip);
}
