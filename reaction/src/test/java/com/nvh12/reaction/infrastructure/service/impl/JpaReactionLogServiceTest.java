package com.nvh12.reaction.infrastructure.service.impl;

import com.nvh12.reaction.infrastructure.persistence.entity.ReactionLogEntity;
import com.nvh12.reaction.infrastructure.persistence.repository.ReactionLogRepository;
import com.nvh12.reaction.service.ReactionEventPort;
import com.nvh12.reaction.service.dto.DDoSInput;
import com.nvh12.reaction.service.dto.DetectionType;
import com.nvh12.reaction.service.dto.NetworkLayer;
import com.nvh12.reaction.service.dto.ReactionAction;
import com.nvh12.reaction.service.dto.Severity;
import com.nvh12.reaction.service.dto.TrafficInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JpaReactionLogServiceTest {

    @Mock ReactionLogRepository repository;
    @Mock ReactionEventPort publisher;

    JpaReactionLogService service;

    @BeforeEach
    void setUp() {
        service = new JpaReactionLogService(repository, publisher);
    }

    private DDoSInput ddosInput() {
        DDoSInput input = new DDoSInput();
        input.setDetectionType(DetectionType.DDOS);
        input.setSourceIp("1.2.3.4");
        input.setSeverity(Severity.HIGH);
        input.setDetectedAt(Instant.parse("2026-01-01T00:00:00Z"));
        input.setWindowStart(Instant.parse("2025-12-31T23:55:00Z"));
        input.setWindowEnd(Instant.parse("2026-01-01T00:00:00Z"));
        return input;
    }

    private static ReactionLogEntity withId(ReactionLogEntity e, Long id) {
        return new ReactionLogEntity(id, e.getDetectionType(), e.getSourceIp(), e.getSeverity(), e.getAction(),
                e.getNetworkLayer(), e.getDetectedAt(), e.getWindowStart(), e.getWindowEnd(), e.getReactedAt());
    }

    @Test
    void save_persistsEntityWithFieldsFromInput() {
        DDoSInput input = ddosInput();
        when(repository.save(any())).thenAnswer(inv -> withId(inv.getArgument(0), 42L));

        service.save(input, ReactionAction.BLOCK);

        ArgumentCaptor<ReactionLogEntity> captor = ArgumentCaptor.forClass(ReactionLogEntity.class);
        verify(repository).save(captor.capture());
        ReactionLogEntity entity = captor.getValue();
        assertThat(entity.getDetectionType()).isEqualTo(DetectionType.DDOS);
        assertThat(entity.getSourceIp()).isEqualTo("1.2.3.4");
        assertThat(entity.getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(entity.getAction()).isEqualTo(ReactionAction.BLOCK);
        assertThat(entity.getDetectedAt()).isEqualTo(input.getDetectedAt());
        assertThat(entity.getWindowStart()).isEqualTo(input.getWindowStart());
        assertThat(entity.getWindowEnd()).isEqualTo(input.getWindowEnd());
        assertThat(entity.getReactedAt()).isNotNull();
    }

    @Test
    void save_networkLayerNull_derivesFromDetectionType() {
        DDoSInput input = ddosInput();
        input.setNetworkLayer(null);
        when(repository.save(any())).thenAnswer(inv -> withId(inv.getArgument(0), 1L));

        service.save(input, ReactionAction.BLOCK);

        ArgumentCaptor<ReactionLogEntity> captor = ArgumentCaptor.forClass(ReactionLogEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getNetworkLayer()).isEqualTo(NetworkLayer.from(DetectionType.DDOS));
    }

    @Test
    void save_networkLayerProvided_usesProvidedValue() {
        DDoSInput input = ddosInput();
        input.setNetworkLayer(NetworkLayer.HTTP);
        when(repository.save(any())).thenAnswer(inv -> withId(inv.getArgument(0), 1L));

        service.save(input, ReactionAction.BLOCK);

        ArgumentCaptor<ReactionLogEntity> captor = ArgumentCaptor.forClass(ReactionLogEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getNetworkLayer()).isEqualTo(NetworkLayer.HTTP);
    }

    @Test
    void save_publishesReactionEventWithSavedId() {
        DDoSInput input = ddosInput();
        when(repository.save(any())).thenAnswer(inv -> withId(inv.getArgument(0), 99L));

        service.save(input, ReactionAction.BLOCK);

        verify(publisher).publish(eq(99L), eq(ReactionAction.BLOCK), eq("1.2.3.4"), eq(Severity.HIGH), any(Instant.class));
    }

    @Test
    void save_nullSourceIp_persistsAndPublishesWithNullTarget() {
        TrafficInput input = new TrafficInput();
        input.setDetectionType(DetectionType.TRAFFIC);
        input.setSourceIp(null);
        input.setSeverity(Severity.MEDIUM);
        input.setDetectedAt(Instant.now());
        when(repository.save(any())).thenAnswer(inv -> withId(inv.getArgument(0), 7L));

        service.save(input, ReactionAction.SCALE_UP);

        ArgumentCaptor<ReactionLogEntity> captor = ArgumentCaptor.forClass(ReactionLogEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getSourceIp()).isNull();
        verify(publisher).publish(eq(7L), eq(ReactionAction.SCALE_UP), eq((String) null), eq(Severity.MEDIUM), any(Instant.class));
    }

    @Test
    void save_repositoryThrows_doesNotPropagateAndSkipsPublish() {
        when(repository.save(any())).thenThrow(new RuntimeException("db down"));

        assertThatNoException().isThrownBy(() -> service.save(ddosInput(), ReactionAction.BLOCK));

        verify(publisher, never()).publish(any(), any(), any(), any(), any());
    }
}
