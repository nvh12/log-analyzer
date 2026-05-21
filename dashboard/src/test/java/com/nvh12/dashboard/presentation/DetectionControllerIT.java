package com.nvh12.dashboard.presentation;

import com.nvh12.dashboard.AbstractContainerIT;
import com.nvh12.dashboard.domain.DetectionType;
import com.nvh12.dashboard.domain.NetworkLayer;
import com.nvh12.dashboard.domain.Severity;
import com.nvh12.dashboard.infrastructure.persistence.entity.DetectionResultEntity;
import com.nvh12.dashboard.infrastructure.persistence.repository.jpa.JpaDetectionResultRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DetectionControllerIT extends AbstractContainerIT {

    @Autowired JpaDetectionResultRepository jpaDetectionResultRepository;

    @Test
    void listDetections_noFilter_returnsPaginatedResults() throws Exception {
        jpaDetectionResultRepository.saveAll(List.of(
                detectionEntity("DDOS", "HIGH"),
                detectionEntity("WEB_ATTACK", "MEDIUM"),
                detectionEntity("TRAFFIC", "LOW")));

        mockMvc.perform(get("/api/detections").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.content", hasSize(2)));
    }

    @Test
    void listDetections_filterByDetectionType_returnsMatchingRows() throws Exception {
        jpaDetectionResultRepository.saveAll(List.of(
                detectionEntity("DDOS", "HIGH"),
                detectionEntity("WEB_ATTACK", "MEDIUM")));

        mockMvc.perform(get("/api/detections").param("detectionType", "DDOS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.content[0].detectionType").value("DDOS"));
    }

    @Test
    void listDetections_filterBySeverity_returnsMatchingRows() throws Exception {
        jpaDetectionResultRepository.saveAll(List.of(
                detectionEntity("DDOS", "HIGH"),
                detectionEntity("TRAFFIC", "LOW")));

        mockMvc.perform(get("/api/detections").param("severity", "HIGH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.content[0].severity").value("HIGH"));
    }

    @Test
    void listDetections_filterByTimeRange_returnsRowsWithinWindow() throws Exception {
        Instant now = Instant.now();
        DetectionResultEntity old = DetectionResultEntity.builder()
                .detectionType(DetectionType.DDOS).severity(Severity.HIGH).anomaly(true)
                .networkLayer(NetworkLayer.HTTP).detectedAt(now.minus(2, ChronoUnit.HOURS)).build();
        DetectionResultEntity recent = detectionEntity("WEB_ATTACK", "LOW");
        jpaDetectionResultRepository.saveAll(List.of(old, recent));

        mockMvc.perform(get("/api/detections")
                        .param("from", now.minus(10, ChronoUnit.MINUTES).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.content[0].detectionType").value("WEB_ATTACK"));
    }

    @Test
    void listDetections_noRows_returnsEmptyPage() throws Exception {
        mockMvc.perform(get("/api/detections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void getDetection_existingId_returnsDetail() throws Exception {
        DetectionResultEntity saved = jpaDetectionResultRepository.save(
                detectionEntity("BRUTE_FORCE", "CRITICAL"));

        mockMvc.perform(get("/api/detections/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId()))
                .andExpect(jsonPath("$.detectionType").value("BRUTE_FORCE"))
                .andExpect(jsonPath("$.severity").value("CRITICAL"))
                .andExpect(jsonPath("$.anomaly").value(true))
                .andExpect(jsonPath("$.payload").isMap());
    }

    @Test
    void getDetection_nonExistentId_returns404() throws Exception {
        mockMvc.perform(get("/api/detections/{id}", 999999L))
                .andExpect(status().isNotFound());
    }

    @Test
    void listDetections_combinedFilters_appliesBothConditions() throws Exception {
        jpaDetectionResultRepository.saveAll(List.of(
                detectionEntity("DDOS", "HIGH"),
                detectionEntity("DDOS", "LOW"),
                detectionEntity("WEB_ATTACK", "HIGH")));

        mockMvc.perform(get("/api/detections")
                        .param("detectionType", "DDOS")
                        .param("severity", "HIGH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.content[0].detectionType").value("DDOS"))
                .andExpect(jsonPath("$.content[0].severity").value("HIGH"));
    }

    @Test
    void listDetections_defaultPagination_uses20PageSize() throws Exception {
        mockMvc.perform(get("/api/detections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(20));
    }

    private DetectionResultEntity detectionEntity(String type, String severity) {
        return DetectionResultEntity.builder()
                .detectionType(DetectionType.valueOf(type)).severity(Severity.valueOf(severity)).anomaly(true)
                .networkLayer(NetworkLayer.HTTP).detectedAt(Instant.now()).build();
    }
}
