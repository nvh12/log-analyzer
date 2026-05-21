package com.nvh12.dashboard.application.port;

public interface BroadcastPort {
    void broadcast(String event, Object payload);
}
