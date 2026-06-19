package com.nvh12.dashboard.presentation;

import com.nvh12.dashboard.AbstractContainerIT;
import com.nvh12.dashboard.application.port.WhitelistPort;
import com.nvh12.dashboard.domain.DetectionType;
import com.nvh12.dashboard.domain.NetworkLayer;
import com.nvh12.dashboard.domain.ReactionAction;
import com.nvh12.dashboard.domain.Severity;
import com.nvh12.dashboard.infrastructure.persistence.entity.ReactionLogEntity;
import com.nvh12.dashboard.infrastructure.persistence.repository.jpa.JpaReactionLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReactionControllerIT extends AbstractContainerIT {

    @Autowired JpaReactionLogRepository jpaReactionLogRepository;
    @Autowired RedisTemplate<String, String> redisTemplate;
    @MockitoBean WhitelistPort whitelistPort;

    // ── GET /api/reactions ──────────────────────────────────────────────────

    @Test
    void listReactions_noFilter_returnsPaginatedResults() throws Exception {
        jpaReactionLogRepository.saveAll(List.of(
                reactionEntity("1.1.1.1", "BLOCK"),
                reactionEntity("2.2.2.2", "RATE_LIMIT"),
                reactionEntity(null, "SCALE_UP")));

        mockMvc.perform(get("/api/reactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.content", hasSize(3)));
    }

    @Test
    void listReactions_filterByAction_returnsMatchingRows() throws Exception {
        jpaReactionLogRepository.saveAll(List.of(
                reactionEntity("1.1.1.1", "BLOCK"),
                reactionEntity("2.2.2.2", "RATE_LIMIT")));

        mockMvc.perform(get("/api/reactions").param("action", "BLOCK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.content[0].action").value("BLOCK"))
                .andExpect(jsonPath("$.content[0].sourceIp").value("1.1.1.1"));
    }

    @Test
    void listReactions_responseMappedCorrectly() throws Exception {
        jpaReactionLogRepository.save(reactionEntity("3.3.3.3", "BLOCK"));

        mockMvc.perform(get("/api/reactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sourceIp").value("3.3.3.3"))
                .andExpect(jsonPath("$.content[0].action").value("BLOCK"))
                .andExpect(jsonPath("$.content[0].severity").value("HIGH"))
                .andExpect(jsonPath("$.content[0].detectionType").value("DDOS"))
                .andExpect(jsonPath("$.content[0].reactedAt").isNotEmpty());
    }

    // ── GET /api/reactions/active ───────────────────────────────────────────

    @Test
    void activeReactions_noBlockedIps_returnsEmptyBlocklist() throws Exception {
        mockMvc.perform(get("/api/reactions/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocklist", hasSize(0)))
                .andExpect(jsonPath("$.rate_limits", hasSize(0)));
    }

    @Test
    void activeReactions_withBlockedIp_returnsBlocklistEntry() throws Exception {
        String ip = "10.0.0.5";
        redisTemplate.opsForSet().add("blocklist:ips", ip);
        redisTemplate.opsForValue().set("blocklist:ip:" + ip, "severity=HIGH");
        redisTemplate.expire("blocklist:ip:" + ip, Duration.ofMinutes(30));

        mockMvc.perform(get("/api/reactions/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocklist[0].ip").value(ip))
                .andExpect(jsonPath("$.blocklist[0].ttl_seconds").isNumber())
                .andExpect(jsonPath("$.blocklist[0].severity").value("HIGH"));
    }

    @Test
    void activeReactions_expiredBlockKey_skipsFromBlocklist() throws Exception {
        String ip = "10.0.0.9";
        redisTemplate.opsForSet().add("blocklist:ips", ip);

        mockMvc.perform(get("/api/reactions/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocklist", hasSize(0)));
    }

    @Test
    void activeReactions_withRateLimitedIp_returnsRateLimitEntry() throws Exception {
        String ip = "10.0.0.7";
        String key = "ratelimit:ip:" + ip + ":limit";
        redisTemplate.opsForValue().set(key, "100");
        redisTemplate.expire(key, Duration.ofMinutes(10));

        mockMvc.perform(get("/api/reactions/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rate_limits[0].ip").value(ip))
                .andExpect(jsonPath("$.rate_limits[0].requests_per_minute").value(100));
    }

    // ── POST /api/reactions/{id}/lift ───────────────────────────────────────

    @Test
    void liftBlock_existingReactionWithIp_removesRedisKeys() throws Exception {
        String ip = "10.0.0.2";
        redisTemplate.opsForSet().add("blocklist:ips", ip);
        redisTemplate.opsForValue().set("blocklist:ip:" + ip, "severity=MEDIUM");
        redisTemplate.expire("blocklist:ip:" + ip, Duration.ofMinutes(10));

        ReactionLogEntity saved = jpaReactionLogRepository.save(reactionEntity(ip, "BLOCK"));

        mockMvc.perform(post("/api/reactions/{id}/lift", saved.getId()))
                .andExpect(status().isOk());

        assertThat(redisTemplate.opsForValue().get("blocklist:ip:" + ip)).isNull();
        assertThat(redisTemplate.opsForSet().isMember("blocklist:ips", ip)).isFalse();
    }

    @Test
    void liftBlock_nonExistentId_returns404() throws Exception {
        mockMvc.perform(post("/api/reactions/{id}/lift", 999999L))
                .andExpect(status().isNotFound());
    }

    @Test
    void liftBlock_reactionWithNullSourceIp_returnsOk() throws Exception {
        ReactionLogEntity saved = jpaReactionLogRepository.save(reactionEntity(null, "SCALE_UP"));

        mockMvc.perform(post("/api/reactions/{id}/lift", saved.getId()))
                .andExpect(status().isOk());
    }

    // ── POST /api/reactions/blocks/lift ─────────────────────────────────────

    @Test
    void liftBlocks_existingBlockedIps_removesRedisKeys() throws Exception {
        String ip1 = "10.0.0.11";
        String ip2 = "10.0.0.12";
        redisTemplate.opsForSet().add("blocklist:ips", ip1, ip2);
        redisTemplate.opsForValue().set("blocklist:ip:" + ip1, "severity=HIGH");
        redisTemplate.opsForValue().set("blocklist:ip:" + ip2, "severity=LOW");

        mockMvc.perform(post("/api/reactions/blocks/lift")
                        .contentType("application/json")
                        .content("[\"" + ip1 + "\",\"" + ip2 + "\"]"))
                .andExpect(status().isOk());

        assertThat(redisTemplate.opsForSet().members("blocklist:ips")).isEmpty();
    }

    // ── GET/PUT /api/reactions/whitelist ────────────────────────────────────

    @Test
    void listWhitelist_returnsIpsFromPort() throws Exception {
        when(whitelistPort.listWhitelistedIps()).thenReturn(List.of("1.1.1.1", "2.2.2.2"));

        mockMvc.perform(get("/api/reactions/whitelist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0]").value("1.1.1.1"));
    }

    @Test
    void replaceWhitelist_delegatesToPort() throws Exception {
        mockMvc.perform(put("/api/reactions/whitelist")
                        .contentType("application/json")
                        .content("[\"3.3.3.3\",\"4.4.4.4\"]"))
                .andExpect(status().isOk());

        verify(whitelistPort).replaceWhitelist(eq(List.of("3.3.3.3", "4.4.4.4")));
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private ReactionLogEntity reactionEntity(String ip, String action) {
        return ReactionLogEntity.builder()
                .detectionType(DetectionType.DDOS).sourceIp(ip).severity(Severity.HIGH)
                .action(ReactionAction.valueOf(action)).networkLayer(NetworkLayer.HTTP)
                .detectedAt(Instant.now()).reactedAt(Instant.now()).build();
    }
}
