package com.nvh12.dashboard.presentation;

import com.nvh12.dashboard.AbstractContainerIT;
import com.nvh12.dashboard.infrastructure.persistence.entity.NormalizedFlowEntity;
import com.nvh12.dashboard.infrastructure.persistence.entity.NormalizedHttpEntity;
import com.nvh12.dashboard.infrastructure.persistence.repository.jpa.JpaFlowLogRepository;
import com.nvh12.dashboard.infrastructure.persistence.repository.jpa.JpaHttpLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LogControllerIT extends AbstractContainerIT {

    @Autowired JpaHttpLogRepository jpaHttpLogRepository;
    @Autowired JpaFlowLogRepository jpaFlowLogRepository;

    // ── HTTP logs ───────────────────────────────────────────────────────────

    @Test
    void listHttpLogs_noFilter_returnsPaginatedResults() throws Exception {
        jpaHttpLogRepository.saveAll(List.of(
                httpEntity("1.1.1.1", 200),
                httpEntity("2.2.2.2", 404),
                httpEntity("3.3.3.3", 500)));

        mockMvc.perform(get("/api/logs/http").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.content", hasSize(2)));
    }

    @Test
    void listHttpLogs_filterByIp_returnsOnlyMatchingRows() throws Exception {
        jpaHttpLogRepository.saveAll(List.of(httpEntity("1.1.1.1", 200), httpEntity("2.2.2.2", 200)));

        mockMvc.perform(get("/api/logs/http").param("ip", "1.1.1.1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.content[0].ip").value("1.1.1.1"));
    }

    @Test
    void listHttpLogs_filterByStatus_returnsOnlyMatchingRows() throws Exception {
        jpaHttpLogRepository.saveAll(List.of(httpEntity("1.1.1.1", 200), httpEntity("2.2.2.2", 404)));

        mockMvc.perform(get("/api/logs/http").param("status", "404"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.content[0].statusCode").value(404));
    }

    @Test
    void listHttpLogs_filterByTimeRange_returnsRowsWithinWindow() throws Exception {
        Instant now = Instant.now();
        NormalizedHttpEntity old = NormalizedHttpEntity.builder()
                .ip("old.ip").method("GET").url("/old").statusCode(200)
                .responseSize(10).processedAt(now.minus(1, ChronoUnit.HOURS)).build();
        NormalizedHttpEntity recent = httpEntity("new.ip", 200);
        jpaHttpLogRepository.saveAll(List.of(old, recent));

        mockMvc.perform(get("/api/logs/http")
                        .param("from", now.minus(5, ChronoUnit.MINUTES).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.content[0].ip").value("new.ip"));
    }

    @Test
    void listHttpLogs_noRows_returnsEmptyPage() throws Exception {
        mockMvc.perform(get("/api/logs/http"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void getHttpLog_existingId_returnsLog() throws Exception {
        NormalizedHttpEntity saved = jpaHttpLogRepository.save(httpEntity("5.5.5.5", 201));

        mockMvc.perform(get("/api/logs/http/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId()))
                .andExpect(jsonPath("$.ip").value("5.5.5.5"))
                .andExpect(jsonPath("$.statusCode").value(201));
    }

    @Test
    void getHttpLog_nonExistentId_returns404() throws Exception {
        mockMvc.perform(get("/api/logs/http/{id}", 999999L))
                .andExpect(status().isNotFound());
    }

    // ── Flow logs ───────────────────────────────────────────────────────────

    @Test
    void listFlowLogs_noFilter_returnsPaginatedResults() throws Exception {
        jpaFlowLogRepository.saveAll(List.of(
                flowEntity("10.0.0.1", 80),
                flowEntity("10.0.0.2", 443)));

        mockMvc.perform(get("/api/logs/flow"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.content", hasSize(2)));
    }

    @Test
    void listFlowLogs_filterBySrcIp_returnsOnlyMatchingRows() throws Exception {
        jpaFlowLogRepository.saveAll(List.of(flowEntity("10.0.0.1", 80), flowEntity("10.0.0.2", 443)));

        mockMvc.perform(get("/api/logs/flow").param("srcIp", "10.0.0.1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.content[0].sourceIp").value("10.0.0.1"));
    }

    @Test
    void listFlowLogs_filterByDstPort_returnsOnlyMatchingRows() throws Exception {
        jpaFlowLogRepository.saveAll(List.of(flowEntity("10.0.0.1", 80), flowEntity("10.0.0.2", 443)));

        mockMvc.perform(get("/api/logs/flow").param("dstPort", "443"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.content[0].destPort").value(443));
    }

    @Test
    void getFlowLog_existingId_returnsLog() throws Exception {
        NormalizedFlowEntity saved = jpaFlowLogRepository.save(flowEntity("192.168.1.1", 22));

        mockMvc.perform(get("/api/logs/flow/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId()))
                .andExpect(jsonPath("$.sourceIp").value("192.168.1.1"))
                .andExpect(jsonPath("$.destPort").value(22));
    }

    @Test
    void getFlowLog_withFeatures_returnsDeserializedFeaturesMap() throws Exception {
        NormalizedFlowEntity saved = jpaFlowLogRepository.save(
                NormalizedFlowEntity.builder()
                        .sourceIp("10.0.0.5").destIp("10.0.0.6")
                        .sourcePort(12345).destPort(443)
                        .features(Map.of("Flow Duration", 500.0, "Total Fwd Packets", 3.0))
                        .processedAt(Instant.now()).build());

        mockMvc.perform(get("/api/logs/flow/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features['Flow Duration']").value(500.0))
                .andExpect(jsonPath("$.features['Total Fwd Packets']").value(3.0));
    }

    @Test
    void getFlowLog_nonExistentId_returns404() throws Exception {
        mockMvc.perform(get("/api/logs/flow/{id}", 999999L))
                .andExpect(status().isNotFound());
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private NormalizedHttpEntity httpEntity(String ip, int statusCode) {
        return NormalizedHttpEntity.builder()
                .ip(ip).method("GET").url("/test").statusCode(statusCode)
                .responseSize(512).processedAt(Instant.now()).build();
    }

    private NormalizedFlowEntity flowEntity(String srcIp, int dstPort) {
        return NormalizedFlowEntity.builder()
                .sourceIp(srcIp).destIp("10.255.0.1")
                .sourcePort(54321).destPort(dstPort)
                .processedAt(Instant.now()).build();
    }
}
