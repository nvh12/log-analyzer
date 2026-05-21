package com.nvh12.reaction.infrastructure.persistence.repository;

import com.nvh12.reaction.infrastructure.persistence.entity.ReactionLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReactionLogRepository extends JpaRepository<ReactionLogEntity, Long> {
}
