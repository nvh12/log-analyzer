package com.nvh12.dashboard.infrastructure.redis;

import com.nvh12.dashboard.application.port.WhitelistPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class WhitelistStore implements WhitelistPort {

    static final String WHITELIST_IPS = "whitelist:ips";

    private final RedisTemplate<String, String> redisTemplate;

    public List<String> listWhitelistedIps() {
        Set<String> ips = redisTemplate.opsForSet().members(WHITELIST_IPS);
        return ips == null ? List.of() : List.copyOf(ips);
    }

    public void replaceWhitelist(List<String> ips) {
        redisTemplate.delete(WHITELIST_IPS);
        if (!ips.isEmpty()) {
            redisTemplate.opsForSet().add(WHITELIST_IPS, ips.toArray(new String[0]));
        }
        log.info("Replaced whitelist with {} IPs", ips.size());
    }
}
