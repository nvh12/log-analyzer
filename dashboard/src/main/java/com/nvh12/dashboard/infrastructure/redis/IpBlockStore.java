package com.nvh12.dashboard.infrastructure.redis;

import com.nvh12.dashboard.application.port.IpBlockPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class IpBlockStore implements IpBlockPort {

    static final String BLOCKLIST_IPS       = "blocklist:ips";
    static final String BLOCKLIST_IP_PREFIX = "blocklist:ip:";
    static final String RATE_LIMIT_PREFIX   = "ratelimit:ip:";
    static final String RATE_LIMIT_SUFFIX   = ":limit";

    private final RedisTemplate<String, String> redisTemplate;

    public List<Map<String, Object>> listBlockedIps() {
        Set<String> ips = redisTemplate.opsForSet().members(BLOCKLIST_IPS);
        if (ips == null || ips.isEmpty()) return List.of();

        List<Map<String, Object>> result = new ArrayList<>();
        for (String ip : ips) {
            String key = BLOCKLIST_IP_PREFIX + ip;
            String meta = redisTemplate.opsForValue().get(key);
            Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            if (ttl == null || ttl < 0) {
                redisTemplate.opsForSet().remove(BLOCKLIST_IPS, ip);
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("ip", ip);
            entry.put("ttl_seconds", ttl);
            parseMeta(meta, entry);
            result.add(entry);
        }
        return result;
    }

    public List<Map<String, Object>> listRateLimitedIps() {
        Set<String> keys = redisTemplate.keys(RATE_LIMIT_PREFIX + "*" + RATE_LIMIT_SUFFIX);
        if (keys == null || keys.isEmpty()) return List.of();

        List<Map<String, Object>> result = new ArrayList<>();
        for (String key : keys) {
            String ip = key.replace(RATE_LIMIT_PREFIX, "").replace(RATE_LIMIT_SUFFIX, "");
            String limit = redisTemplate.opsForValue().get(key);
            Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            if (ttl == null || ttl < 0) continue;
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("ip", ip);
            entry.put("requests_per_minute", limit != null ? parseSafeInt(limit) : null);
            entry.put("ttl_seconds", ttl);
            result.add(entry);
        }
        return result;
    }

    public void liftBlock(String ip) {
        redisTemplate.delete(BLOCKLIST_IP_PREFIX + ip);
        redisTemplate.opsForSet().remove(BLOCKLIST_IPS, ip);
        log.info("Lifted IP block for {}", ip);
    }

    private Integer parseSafeInt(String value) {
        try { return Integer.parseInt(value.trim()); } catch (NumberFormatException e) { return null; }
    }

    private void parseMeta(String meta, Map<String, Object> into) {
        if (meta == null) return;
        for (String part : meta.split(";")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2) into.put(kv[0], kv[1]);
        }
    }
}
