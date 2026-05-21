package com.nvh12.dashboard.application.port;

import com.nvh12.dashboard.application.PageView;
import com.nvh12.dashboard.application.ReactionSummaryView;
import com.nvh12.dashboard.domain.ReactionAction;

import java.time.Instant;
import java.util.Optional;

public interface ReactionRepository {
    PageView<ReactionSummaryView> findFiltered(ReactionAction action, Instant from, Instant to, int page, int size);
    Optional<ReactionSummaryView> findById(Long id);
}
