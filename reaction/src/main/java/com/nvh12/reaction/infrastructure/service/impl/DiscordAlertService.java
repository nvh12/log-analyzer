package com.nvh12.reaction.infrastructure.service.impl;

import com.nvh12.reaction.infrastructure.config.AlertProperties;
import com.nvh12.reaction.service.AlertChannel;
import com.nvh12.reaction.service.dto.Severity;
import com.nvh12.reaction.service.dto.alert.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.nvh12.reaction.infrastructure.service.impl.AlertHtmlTemplate.fmtInstant;

@Slf4j
@Service
@ConditionalOnExpression("!'${alert.discord.webhook-url:}'.isEmpty()")
public class DiscordAlertService implements AlertChannel {

    private final AlertProperties alertProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
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
        String payload;
        try {
            payload = buildSinglePayload(alert);
        } catch (Exception e) {
            log.error("Failed to build Discord payload [type={} ip={} severity={}]: {}",
                    alert.getDetectionType(), alert.getSourceIp(), alert.getSeverity(), e.getMessage());
            return;
        }
        sendWebhook(payload, "type=%s ip=%s severity=%s".formatted(
                alert.getDetectionType(), alert.getSourceIp(), alert.getSeverity()));
    }

    @Override
    public void alertBatch(List<Alert> alerts) {
        if (alerts.isEmpty()) return;
        String payload;
        try {
            payload = buildBatchPayload(alerts);
        } catch (Exception e) {
            log.error("Failed to build Discord batch payload [type={} count={}]: {}",
                    alerts.get(0).getDetectionType(), alerts.size(), e.getMessage());
            return;
        }
        sendWebhook(payload, "type=%s count=%d".formatted(alerts.get(0).getDetectionType(), alerts.size()));
    }

    private void sendWebhook(String payload, String context) {
        String webhookUrl = alertProperties.getDiscord().getWebhookUrl();
        if (webhookUrl == null || webhookUrl.isBlank()) return;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("Discord webhook returned {} [{}]", response.statusCode(), context);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Discord alert interrupted [{}]", context);
        } catch (IOException e) {
            log.error("Failed to send Discord alert [{}]: {}", context, e.getMessage());
        }
    }

    private String buildSinglePayload(Alert alert) throws Exception {
        ObjectNode embed = objectMapper.createObjectNode();
        embed.put("title", severityEmoji(alert.getSeverity()) + " " + alert.getDetectionType() + " Detected");
        embed.put("color", severityColor(alert.getSeverity()));
        embed.put("timestamp", alert.getDetectedAt().toString());

        ArrayNode fields = embed.putArray("fields");
        addField(fields, "Source IP", alert.getSourceIp(), true);
        addField(fields, "Severity", alert.getSeverity().toString(), true);
        addField(fields, "Detection Type", alert.getDetectionType().toString(), true);
        addField(fields, "Detected At", fmtInstant(alert.getDetectedAt()), false);
        addField(fields, "Window", fmtInstant(alert.getWindowStart()) + " → " + fmtInstant(alert.getWindowEnd()), false);

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

    private String buildBatchPayload(List<Alert> alerts) throws Exception {
        Severity maxSeverity = alerts.stream()
                .map(Alert::getSeverity)
                .max(Comparator.naturalOrder())
                .orElse(alerts.get(0).getSeverity());

        Instant earliest = alerts.stream()
                .map(Alert::getDetectedAt)
                .filter(t -> t != null)
                .min(Comparator.naturalOrder())
                .orElse(alerts.get(0).getDetectedAt());
        Instant latest = alerts.stream()
                .map(Alert::getDetectedAt)
                .filter(t -> t != null)
                .max(Comparator.naturalOrder())
                .orElse(alerts.get(0).getDetectedAt());

        Map<Severity, Long> severityCounts = alerts.stream()
                .collect(Collectors.groupingBy(Alert::getSeverity, Collectors.counting()));
        String severityBreakdown = severityCounts.entrySet().stream()
                .sorted(Map.Entry.<Severity, Long>comparingByKey().reversed())
                .map(e -> e.getKey() + ": " + e.getValue())
                .collect(Collectors.joining(", "));

        String sourceIps = alerts.stream()
                .map(Alert::getSourceIp)
                .filter(ip -> ip != null && !ip.isBlank())
                .distinct()
                .limit(10)
                .collect(Collectors.joining(", "));

        int n = alerts.size();
        ObjectNode embed = objectMapper.createObjectNode();
        embed.put("title", severityEmoji(maxSeverity) + " " + n + " " + alerts.get(0).getDetectionType()
                + " Alert" + (n != 1 ? "s" : ""));
        embed.put("color", severityColor(maxSeverity));
        embed.put("timestamp", latest.toString());

        ArrayNode fields = embed.putArray("fields");
        addField(fields, "Count", String.valueOf(n), true);
        addField(fields, "Max Severity", maxSeverity.toString(), true);
        addField(fields, "Severity Breakdown", severityBreakdown, false);
        addField(fields, "Source IPs", sourceIps.isBlank() ? "N/A" : sourceIps, false);
        addField(fields, "Time Range", fmtInstant(earliest) + " → " + fmtInstant(latest), false);

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
            case HIGH -> "🟠";
            case MEDIUM -> "🟡";
            case LOW -> "🟢";
            case NONE -> throw new IllegalStateException("NONE severity should not reach alerting");
        };
    }

    private static int severityColor(Severity severity) {
        return switch (severity) {
            case CRITICAL -> 0xdc2626;
            case HIGH -> 0xea580c;
            case MEDIUM -> 0xd97706;
            case LOW -> 0x16a34a;
            case NONE -> throw new IllegalStateException("NONE severity should not reach alerting");
        };
    }
}
