package com.nvh12.logprocessing.domain.service;

import com.nvh12.logprocessing.domain.model.NormalizedLog;

public interface EventService {
    void publish(NormalizedLog log);
}
