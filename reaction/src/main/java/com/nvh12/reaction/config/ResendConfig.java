package com.nvh12.reaction.config;

import com.resend.Resend;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "alert.provider", havingValue = "resend")
public class ResendConfig {

    @Bean
    public Resend resendClient(AlertProperties alertProperties) {
        String apiKey = alertProperties.getResend().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "alert.resend.api-key must be set when alert.provider=resend");
        }
        return new Resend(apiKey);
    }
}
