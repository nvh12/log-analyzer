package com.nvh12.reaction.infrastructure.persistence.repository;

import com.nvh12.reaction.infrastructure.persistence.entity.DroppedReactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DroppedReactionRepository extends JpaRepository<DroppedReactionEntity, Long> {
}
