package com.nvh12.dashboard.infrastructure.persistence.repository;

import com.nvh12.dashboard.application.DetectionDetailView;
import com.nvh12.dashboard.application.DetectionSummaryView;
import com.nvh12.dashboard.application.PageView;
import com.nvh12.dashboard.application.port.DetectionRepository;
import com.nvh12.dashboard.domain.DetectionType;
import com.nvh12.dashboard.domain.Severity;
import com.nvh12.dashboard.infrastructure.persistence.entity.DetectionResultEntity;
import com.nvh12.dashboard.infrastructure.persistence.repository.jpa.JpaDetectionResultRepository;
import com.nvh12.dashboard.infrastructure.persistence.repository.mapper.DetectionMapper;
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
public class DetectionRepositoryImpl implements DetectionRepository {

    private final JpaDetectionResultRepository jpa;

    @Override
    public PageView<DetectionSummaryView> findFiltered(DetectionType detectionType, Severity severity, Instant from, Instant to, int page, int size) {
        Specification<DetectionResultEntity> spec = (r, q, cb) -> cb.conjunction();
        if (detectionType != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("detectionType"), detectionType));
        if (severity      != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("severity"), severity));
        if (from          != null) spec = spec.and((r, q, cb) -> cb.greaterThanOrEqualTo(r.<Instant>get("detectedAt"), from));
        if (to            != null) spec = spec.and((r, q, cb) -> cb.lessThanOrEqualTo(r.<Instant>get("detectedAt"), to));
        Page<DetectionResultEntity> p = jpa.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "detectedAt")));
        return new PageView<>(p.getContent().stream().map(DetectionMapper::toSummary).toList(),
                p.getNumber(), p.getSize(), p.getTotalElements());
    }

    @Override
    public Optional<DetectionDetailView> findById(Long id) {
        return jpa.findById(id).map(DetectionMapper::toDetail);
    }
}
