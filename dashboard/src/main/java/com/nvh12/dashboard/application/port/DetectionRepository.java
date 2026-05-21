package com.nvh12.dashboard.application.port;

import com.nvh12.dashboard.application.DetectionDetailView;
import com.nvh12.dashboard.application.DetectionSummaryView;
import com.nvh12.dashboard.application.PageView;
import com.nvh12.dashboard.domain.DetectionType;
import com.nvh12.dashboard.domain.Severity;

import java.time.Instant;
import java.util.Optional;

public interface DetectionRepository {
    PageView<DetectionSummaryView> findFiltered(DetectionType detectionType, Severity severity, Instant from, Instant to, int page, int size);
    Optional<DetectionDetailView> findById(Long id);
}
