package com.nvh12.reaction.service;

import com.nvh12.reaction.service.dto.Severity;

public interface IpBlockService {

    void block(String ip, Severity severity);

    boolean isBlocked(String ip);
}
