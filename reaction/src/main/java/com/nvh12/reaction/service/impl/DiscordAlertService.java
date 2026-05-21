package com.nvh12.reaction.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nvh12.reaction.config.AlertProperties;
import com.nvh12.reaction.service.AlertChannel;
import com.nvh12.reaction.service.dto.Severity;
import com.nvh12.reaction.service.dto.alert.Alert;
import com.nvh12.reaction.service.dto.alert.BruteForceAlert;
import com.nvh12.reaction.service.dto.alert.DDoSAlert;
import com.nvh12.reaction.service.dto.alert.TrafficAlert;
import com.nvh12.reaction.service.dto.alert.WebAttackAlert;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Slf4j
@Service
@ConditionalOnProperty(name = "alert.discord.webhook-url")
public class DiscordAlertService implements AlertChannel {

    private final AlertProperties alertProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public DiscordAlertService(AlertProperties alertProperties, ObjectMapper objectMapper) {
        this(alertProperties, objectMapper, HttpClient.newHttpClient());
    }

    DiscordAlertService(AlertProperties alertProperties, ObjectMapper objectMapper, HttpClient httpClient) {
        this.alertProperties = alertProperties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public void alert(Alert alert) {
        String webhookUrl = alertProperties.getDiscord().getWebhookUrl();
        if (webhookUrl == null || webhookUrl.isBlank()) {
            return;
        }
        String payload;
        try {
            payload = buildPayload(alert);
        } catch (Exception e) {
            log.error("Failed to build Discord payload [type={} ip={} severity={}]: {}",
                    alert.getDetectionType(), alert.getSourceIp(), alert.getSeverity(), e.getMessage());
            return;
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("Discord webhook returned {} [type={} ip={} severity={}]",
                        response.statusCode(), alert.getDetectionType(), alert.getSourceIp(),
                        alert.getSeverity());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Discord alert interrupted [type={} ip={} severity={}]",
                    alert.getDetectionType(), alert.getSourceIp(), alert.getSeverity());
        } catch (IOException e) {
            log.error("Failed to send Discord alert [type={} ip={} severity={}]: {}",
                    alert.getDetectionType(), alert.getSourceIp(), alert.getSeverity(), e.getMessage());
        }
    }

    private String buildPayload(Alert alert) throws Exception {
        ObjectNode embed = objectMapper.createObjectNode();
        embed.put("title", severityEmoji(alert.getSeverity()) + " " + alert.getDetectionType() + " Detected");
        embed.put("color", severityColor(alert.getSeverity()));
        embed.put("timestamp", alert.getDetectedAt().toString());

        ArrayNode fields = embed.putArray("fields");
        addField(fields, "Source IP",      alert.getSourceIp(),                  true);
        addField(fields, "Severity",       alert.getSeverity().toString(),        true);
        addField(fields, "Detection Type", alert.getDetectionType().toString(),   true);
        addField(fields, "Detected At",    alert.getDetectedAt().toString(),      false);
        addField(fields, "Window",         alert.getWindowStart() + " → " + alert.getWindowEnd(), false);

        if (alert instanceof TrafficAlert traffic && traffic.getMethodFlags() != null) {
            addField(fields, "Methods", traffic.getMethodFlags().toDisplayString(), true);
        } else if (alert instanceof DDoSAlert ddos) {
            addField(fields, "Destination", ddos.getDestIp() + ":" + ddos.getDestPort(), true);
        } else if (alert instanceof WebAttackAlert webAttack) {
            addField(fields, "Layer Triggered", webAttack.getLayerTriggered(), true);
        } else if (alert instanceof BruteForceAlert bruteForce) {
            addField(fields, "Destination", bruteForce.getDestIp() + ":" + bruteForce.getDestPort(), true);
        }

        ObjectNode footer = objectMapper.createObjectNode();
        footer.put("text", "Log Analyzer — automated security alert");
        embed.set("footer", footer);

        ObjectNode root = objectMapper.createObjectNode();
        root.putArray("embeds").add(embed);
        return objectMapper.writeValueAsString(root);
    }

    private static void addField(ArrayNode fields, String name, String value, boolean inline) {
        ObjectNode field = fields.addObject();
        field.put("name", name);
        field.put("value", value != null ? value : "");
        field.put("inline", inline);
    }

    private static String severityEmoji(Severity severity) {
        return switch (severity) {
            case CRITICAL -> "🔴";
            case HIGH     -> "🟠";
            case MEDIUM   -> "🟡";
            case LOW      -> "🟢";
        };
    }

    private static int severityColor(Severity severity) {
        return switch (severity) {
            case CRITICAL -> 0xdc2626;
            case HIGH     -> 0xea580c;
            case MEDIUM   -> 0xd97706;
            case LOW      -> 0x16a34a;
        };
    }
}
