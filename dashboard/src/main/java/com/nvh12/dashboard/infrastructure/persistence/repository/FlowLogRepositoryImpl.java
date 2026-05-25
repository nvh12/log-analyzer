package com.nvh12.dashboard.infrastructure.persistence.repository;

import com.nvh12.dashboard.application.FlowLogView;
import com.nvh12.dashboard.application.PageView;
import com.nvh12.dashboard.application.port.FlowLogRepository;
import com.nvh12.dashboard.infrastructure.persistence.entity.NormalizedFlowEntity;
import com.nvh12.dashboard.infrastructure.persistence.repository.jpa.JpaFlowLogRepository;
import com.nvh12.dashboard.infrastructure.persistence.mapper.FlowLogMapper;
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
public class FlowLogRepositoryImpl implements FlowLogRepository {

    private final JpaFlowLogRepository jpa;

    @Override
    public PageView<FlowLogView> findFiltered(String srcIp, Integer dstPort, Instant from, Instant to, int page, int size) {
        Specification<NormalizedFlowEntity> spec = (r, q, cb) -> cb.conjunction();
        if (srcIp   != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("sourceIp"), srcIp));
        if (dstPort != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("destPort"), dstPort));
        if (from    != null) spec = spec.and((r, q, cb) -> cb.greaterThanOrEqualTo(r.<Instant>get("processedAt"), from));
        if (to      != null) spec = spec.and((r, q, cb) -> cb.lessThanOrEqualTo(r.<Instant>get("processedAt"), to));
        Page<NormalizedFlowEntity> p = jpa.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "processedAt")));
        return new PageView<>(p.getContent().stream().map(FlowLogMapper::toView).toList(),
                p.getNumber(), p.getSize(), p.getTotalElements());
    }

    @Override
    public Optional<FlowLogView> findById(Long id) {
        return jpa.findById(id).map(FlowLogMapper::toView);
    }

    @Override
    public long countSince(Instant since) {
        return jpa.countSince(since);
    }
}
