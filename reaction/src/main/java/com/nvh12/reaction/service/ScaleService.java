package com.nvh12.reaction.service;

import com.nvh12.reaction.service.dto.DetectionType;
import com.nvh12.reaction.service.dto.Severity;

public interface ScaleService {

    void requestScale(DetectionType detectionType, Severity severity);
}
