package com.nvh12.dashboard.infrastructure.persistence.mapper;

import com.nvh12.dashboard.application.DetectionDetailView;
import com.nvh12.dashboard.application.DetectionSummaryView;
import com.nvh12.dashboard.domain.DetectionType;
import com.nvh12.dashboard.infrastructure.persistence.entity.DetectionResultEntity;

import java.util.LinkedHashMap;
import java.util.Map;

public class DetectionMapper {

    private DetectionMapper() {}

    public static DetectionSummaryView toSummary(DetectionResultEntity e) {
        return new DetectionSummaryView(e.getId(), e.getDetectionType(), e.getSeverity(),
                e.getAnomaly(), e.getConfidence(), e.getSourceIp(), e.getDetectedAt());
    }

    public static DetectionDetailView toDetail(DetectionResultEntity e) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (e.getDetectionType() == DetectionType.TRAFFIC && e.getMethodFlags() != null) {
            payload.put("method_flags", e.getMethodFlags());
        }
        if (e.getDetectionType() == DetectionType.WEB_ATTACK && e.getLayerTriggered() != null) {
            payload.put("layer_triggered", e.getLayerTriggered());
        }
        return new DetectionDetailView(e.getId(), e.getDetectionType(), e.getSeverity(),
                e.getAnomaly(), e.getConfidence(), e.getNetworkLayer(), e.getSourceIp(),
                e.getDestIp(), e.getDestPort(), e.getLogTimestamp(), e.getWindowStart(),
                e.getWindowEnd(), e.getDetectedAt(), payload);
    }
}
