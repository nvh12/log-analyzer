package com.nvh12.dashboard.infrastructure.persistence.repository.jpa;

import com.nvh12.dashboard.infrastructure.persistence.entity.DetectionResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface JpaDetectionResultRepository extends JpaRepository<DetectionResultEntity, Long>,
        JpaSpecificationExecutor<DetectionResultEntity> {
}
