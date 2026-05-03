package com.nvh12.log_processing.infrastructure.persistence.repository;

import com.nvh12.log_processing.infrastructure.persistence.entity.NormalizedFlowEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NormalizedFlowJpaRepository extends JpaRepository<NormalizedFlowEntity, Long> {
}
