package com.nvh12.dashboard.infrastructure.persistence.mapper;

import com.nvh12.dashboard.application.FlowLogView;
import com.nvh12.dashboard.infrastructure.persistence.entity.NormalizedFlowEntity;

public class FlowLogMapper {

    private FlowLogMapper() {}

    public static FlowLogView toView(NormalizedFlowEntity e) {
        return new FlowLogView(e.getId(), e.getTimestamp(), e.getSourceIp(), e.getDestIp(),
                e.getSourcePort(), e.getDestPort(), e.getFeatures(), e.getProcessedAt());
    }
}
