package com.nvh12.dashboard.application.port;

import com.nvh12.dashboard.application.HttpLogView;
import com.nvh12.dashboard.application.PageView;

import java.time.Instant;
import java.util.Optional;

public interface HttpLogRepository {
    PageView<HttpLogView> findFiltered(String ip, Integer statusCode, Instant from, Instant to, int page, int size);
    Optional<HttpLogView> findById(Long id);
    long countSince(Instant since);
}
