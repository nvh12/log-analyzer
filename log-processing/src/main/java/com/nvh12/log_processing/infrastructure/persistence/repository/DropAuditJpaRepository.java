package com.nvh12.log_processing.infrastructure.persistence.repository;

import com.nvh12.log_processing.infrastructure.persistence.entity.DropAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DropAuditJpaRepository extends JpaRepository<DropAuditEntity, Long> {
}
