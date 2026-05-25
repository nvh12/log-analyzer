package com.nvh12.dashboard.infrastructure.persistence.repository;

import com.nvh12.dashboard.application.HttpLogView;
import com.nvh12.dashboard.application.PageView;
import com.nvh12.dashboard.application.port.HttpLogRepository;
import com.nvh12.dashboard.infrastructure.persistence.entity.NormalizedHttpEntity;
import com.nvh12.dashboard.infrastructure.persistence.repository.jpa.JpaHttpLogRepository;
import com.nvh12.dashboard.infrastructure.persistence.mapper.HttpLogMapper;
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
public class HttpLogRepositoryImpl implements HttpLogRepository {

    private final JpaHttpLogRepository jpa;

    @Override
    public PageView<HttpLogView> findFiltered(String ip, Integer statusCode, Instant from, Instant to, int page, int size) {
        Specification<NormalizedHttpEntity> spec = (r, q, cb) -> cb.conjunction();
        if (ip         != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("ip"), ip));
        if (statusCode != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("statusCode"), statusCode));
        if (from       != null) spec = spec.and((r, q, cb) -> cb.greaterThanOrEqualTo(r.<Instant>get("processedAt"), from));
        if (to         != null) spec = spec.and((r, q, cb) -> cb.lessThanOrEqualTo(r.<Instant>get("processedAt"), to));
        Page<NormalizedHttpEntity> p = jpa.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "processedAt")));
        return new PageView<>(p.getContent().stream().map(HttpLogMapper::toView).toList(),
                p.getNumber(), p.getSize(), p.getTotalElements());
    }

    @Override
    public Optional<HttpLogView> findById(Long id) {
        return jpa.findById(id).map(HttpLogMapper::toView);
    }

    @Override
    public long countSince(Instant since) {
        return jpa.countSince(since);
    }
}
