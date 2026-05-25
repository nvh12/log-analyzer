package com.nvh12.dashboard.infrastructure.persistence.mapper;

import com.nvh12.dashboard.application.ReactionSummaryView;
import com.nvh12.dashboard.infrastructure.persistence.entity.ReactionLogEntity;

public class ReactionMapper {

    private ReactionMapper() {}

    public static ReactionSummaryView toView(ReactionLogEntity e) {
        return new ReactionSummaryView(e.getId(), e.getDetectionType(), e.getSourceIp(),
                e.getSeverity(), e.getAction(), e.getNetworkLayer(), e.getDetectedAt(), e.getReactedAt());
    }
}
