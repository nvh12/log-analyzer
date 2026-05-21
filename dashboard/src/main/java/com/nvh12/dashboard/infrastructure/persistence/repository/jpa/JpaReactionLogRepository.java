package com.nvh12.dashboard.infrastructure.persistence.repository.jpa;

import com.nvh12.dashboard.infrastructure.persistence.entity.ReactionLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface JpaReactionLogRepository extends JpaRepository<ReactionLogEntity, Long>,
        JpaSpecificationExecutor<ReactionLogEntity> {
}
