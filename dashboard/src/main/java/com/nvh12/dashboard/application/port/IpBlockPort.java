package com.nvh12.dashboard.application.port;

import java.util.List;
import java.util.Map;

public interface IpBlockPort {
    List<Map<String, Object>> listBlockedIps();
    List<Map<String, Object>> listRateLimitedIps();
    void liftBlock(String ip);
}
