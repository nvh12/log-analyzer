package com.nvh12.dashboard.infrastructure.persistence.repository.jpa;

import com.nvh12.dashboard.infrastructure.persistence.entity.NormalizedFlowEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface JpaFlowLogRepository extends JpaRepository<NormalizedFlowEntity, Long>,
        JpaSpecificationExecutor<NormalizedFlowEntity> {

    @Query("SELECT COUNT(e) FROM NormalizedFlowEntity e WHERE e.processedAt > :since")
    long countSince(@Param("since") Instant since);
}
