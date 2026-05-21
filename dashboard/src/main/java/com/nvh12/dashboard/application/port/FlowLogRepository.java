package com.nvh12.dashboard.application.port;

import com.nvh12.dashboard.application.FlowLogView;
import com.nvh12.dashboard.application.PageView;

import java.time.Instant;
import java.util.Optional;

public interface FlowLogRepository {
    PageView<FlowLogView> findFiltered(String srcIp, Integer dstPort, Instant from, Instant to, int page, int size);
    Optional<FlowLogView> findById(Long id);
    long countSince(Instant since);
}
