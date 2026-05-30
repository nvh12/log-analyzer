package com.nvh12.reaction.service.dto;

public enum NetworkLayer {
    HTTP, FLOW;

    public static NetworkLayer from(DetectionType type) {
        return switch (type) {
            case WEB_ATTACK, TRAFFIC -> HTTP;
            case DDOS, BRUTE_FORCE -> FLOW;
        };
    }
}
