package com.nvh12.dashboard.infrastructure.persistence.repository;

import com.nvh12.dashboard.application.PageView;
import com.nvh12.dashboard.application.ReactionSummaryView;
import com.nvh12.dashboard.application.port.ReactionRepository;
import com.nvh12.dashboard.domain.ReactionAction;
import com.nvh12.dashboard.infrastructure.persistence.entity.ReactionLogEntity;
import com.nvh12.dashboard.infrastructure.persistence.repository.jpa.JpaReactionLogRepository;
import com.nvh12.dashboard.infrastructure.persistence.mapper.ReactionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ReactionRepositoryImpl implements ReactionRepository {

    private final JpaReactionLogRepository jpa;

    @Override
    public PageView<ReactionSummaryView> findFiltered(ReactionAction action, Instant from, Instant to, int page, int size) {
        Specification<ReactionLogEntity> spec = (r, q, cb) -> cb.conjunction();
        if (action != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("action"), action));
        if (from   != null) spec = spec.and((r, q, cb) -> cb.greaterThanOrEqualTo(r.<Instant>get("reactedAt"), from));
        if (to     != null) spec = spec.and((r, q, cb) -> cb.lessThanOrEqualTo(r.<Instant>get("reactedAt"), to));
        Page<ReactionLogEntity> p = jpa.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "reactedAt")));
        return new PageView<>(p.getContent().stream().map(ReactionMapper::toView).toList(),
                p.getNumber(), p.getSize(), p.getTotalElements());
    }

    @Override
    public Optional<ReactionSummaryView> findById(Long id) {
        return jpa.findById(id).map(ReactionMapper::toView);
    }
}
