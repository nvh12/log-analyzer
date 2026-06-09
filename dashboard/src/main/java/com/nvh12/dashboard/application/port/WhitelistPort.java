package com.nvh12.dashboard.application.port;

import java.util.List;

public interface WhitelistPort {
    List<String> listWhitelistedIps();
    void replaceWhitelist(List<String> ips);
}
