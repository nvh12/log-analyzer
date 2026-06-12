package com.nvh12.reaction.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "alert")
public class AlertProperties {

    private String provider = "smtp";
    private int batchIntervalSeconds = 180;
    private Mail mail = new Mail();
    private Resend resend = new Resend();
    private Discord discord = new Discord();

    @Getter
    @Setter
    public static class Mail {
        private String from;
        private String to;
    }

    @Getter
    @Setter
    public static class Resend {
        private String apiKey;
    }

    @Getter
    @Setter
    public static class Discord {
        private String webhookUrl;
    }
}
