package com.nvh12.dashboard.infrastructure.persistence.mapper;

import com.nvh12.dashboard.application.HttpLogView;
import com.nvh12.dashboard.infrastructure.persistence.entity.NormalizedHttpEntity;

public class HttpLogMapper {

    private HttpLogMapper() {}

    public static HttpLogView toView(NormalizedHttpEntity e) {
        return new HttpLogView(e.getId(), e.getTimestamp(), e.getIp(), e.getMethod(),
                e.getUrl(), e.getStatusCode(), e.getResponseSize(), e.getQueryString(),
                e.getUserAgent(), e.getReferer(), e.getProcessedAt());
    }
}
