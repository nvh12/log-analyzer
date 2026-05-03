package com.nvh12.log_processing.infrastructure.persistence.repository;

import com.nvh12.log_processing.infrastructure.persistence.entity.NormalizedHttpEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NormalizedHttpJpaRepository extends JpaRepository<NormalizedHttpEntity, Long> {
}
