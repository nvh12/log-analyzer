package com.nvh12.reaction.presentation;

import com.nvh12.reaction.AbstractContainerIT;
import com.nvh12.reaction.config.RabbitMqConfig;
import com.nvh12.reaction.service.dto.*;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class DetectionResultConsumerIT extends AbstractContainerIT {

    @Autowired RabbitTemplate rabbitTemplate;
    @Autowired StringRedisTemplate redisTemplate;
    @Autowired JdbcTemplate jdbcTemplate;

    private static final String IP = "10.0.0.99";

    // RedisIpBlockService constants
    private static final String WHITELIST_IPS        = "whitelist:ips";
    private static final String BLOCKLIST_IPS        = "blocklist:ips";
    private static final String BLOCKLIST_IP_PREFIX  = "blocklist:ip:";

    // RedisRateLimitService constants
    private static final String COUNTER_PREFIX  = "ratelimit:ip:";
    private static final String LIMIT_SUFFIX    = ":limit";

    // RedisScaleService constants
    private static final String SCALE_STATE    = "scale:state";

    // BruteForceReactionService constants
    private static final int ESCALATION_THRESHOLD  = 3;

    @Test
    void ddosDetection_firstAttempt_appliesRateLimit() {
        DDoSInput input = ddosInput(IP, Severity.HIGH);

        rabbitTemplate.convertAndSend(RabbitMqConfig.QUEUE_DETECTION_RESULTS, input);

        String limitKey = COUNTER_PREFIX + IP + LIMIT_SUFFIX;
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(redisTemplate.hasKey(limitKey)).isTrue()
        );
        assertThat(redisTemplate.hasKey(BLOCKLIST_IP_PREFIX + IP)).isFalse();
    }

    @Test
    void ddosDetection_atEscalationThreshold_escalatesToBlock() {
        DDoSInput input = ddosInput(IP, Severity.HIGH);

        for (int i = 0; i < ESCALATION_THRESHOLD; i++) {
            rabbitTemplate.convertAndSend(RabbitMqConfig.QUEUE_DETECTION_RESULTS, input);
        }

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(redisTemplate.hasKey(BLOCKLIST_IP_PREFIX + IP)).isTrue()
        );
    }

    @Test
    void webAttackDetection_blocksSourceIp() {
        WebAttackInput input = new WebAttackInput();
        input.setDetectionType(DetectionType.WEB_ATTACK);
        input.setSourceIp(IP);
        input.setSeverity(Severity.MEDIUM);
        input.setDetectedAt(Instant.now());

        rabbitTemplate.convertAndSend(RabbitMqConfig.QUEUE_DETECTION_RESULTS, input);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(redisTemplate.hasKey(BLOCKLIST_IP_PREFIX + IP)).isTrue()
        );
    }

    @Test
    void trafficDetection_setsScaleState() {
        TrafficInput input = new TrafficInput();
        input.setDetectionType(DetectionType.TRAFFIC);
        input.setSourceIp(IP);
        input.setSeverity(Severity.HIGH);
        input.setDetectedAt(Instant.now());

        rabbitTemplate.convertAndSend(RabbitMqConfig.QUEUE_DETECTION_RESULTS, input);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(redisTemplate.opsForValue().get(SCALE_STATE)).isEqualTo("scaled_up")
        );
    }

    @Test
    void bruteForceDetection_firstAttempt_appliesRateLimit() {
        BruteForceInput input = bruteForceInput(IP, Severity.MEDIUM);

        rabbitTemplate.convertAndSend(RabbitMqConfig.QUEUE_DETECTION_RESULTS, input);

        String limitKey = COUNTER_PREFIX + IP + LIMIT_SUFFIX;
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(redisTemplate.hasKey(limitKey)).isTrue()
        );
        assertThat(redisTemplate.hasKey(BLOCKLIST_IP_PREFIX + IP)).isFalse();
    }

    @Test
    void bruteForceDetection_atEscalationThreshold_escalatesToBlock() {
        BruteForceInput input = bruteForceInput(IP, Severity.HIGH);

        for (int i = 0; i < ESCALATION_THRESHOLD; i++) {
            rabbitTemplate.convertAndSend(RabbitMqConfig.QUEUE_DETECTION_RESULTS, input);
        }

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(redisTemplate.hasKey(BLOCKLIST_IP_PREFIX + IP)).isTrue()
        );
    }

    @Test
    void ddosDetectionForWhitelistedIp_doesNotBlock() {
        redisTemplate.opsForSet().add(WHITELIST_IPS, IP);

        rabbitTemplate.convertAndSend(RabbitMqConfig.QUEUE_DETECTION_RESULTS, ddosInput(IP, Severity.CRITICAL));

        // Wait for the reaction log row — proves the consumer finished processing before checking the negative.
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM reaction.reaction_logs", Long.class))
                        .isGreaterThan(0)
        );
        assertThat(redisTemplate.opsForSet().isMember(BLOCKLIST_IPS, IP)).isFalse();
        assertThat(redisTemplate.hasKey(BLOCKLIST_IP_PREFIX + IP)).isFalse();
    }

    private DDoSInput ddosInput(String ip, Severity severity) {
        DDoSInput input = new DDoSInput();
        input.setDetectionType(DetectionType.DDOS);
        input.setSourceIp(ip);
        input.setSeverity(severity);
        input.setDetectedAt(Instant.now());
        return input;
    }

    private BruteForceInput bruteForceInput(String ip, Severity severity) {
        BruteForceInput input = new BruteForceInput();
        input.setDetectionType(DetectionType.BRUTE_FORCE);
        input.setSourceIp(ip);
        input.setSeverity(severity);
        input.setDetectedAt(Instant.now());
        return input;
    }
}
