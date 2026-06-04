package com.nvh12.reaction.service.impl;

import com.nvh12.reaction.service.dto.Severity;
import com.nvh12.reaction.service.dto.alert.*;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

final class AlertHtmlTemplate {

    private AlertHtmlTemplate() {
    }

    static String buildSubject(Alert alert) {
        return "[%s] %s detected from %s".formatted(
                alert.getSeverity(),
                alert.getDetectionType(),
                alert.getSourceIp()
        );
    }

    static String buildBatchSubject(List<Alert> alerts) {
        Severity maxSeverity = alerts.stream()
                .map(Alert::getSeverity)
                .max(Comparator.naturalOrder())
                .orElse(alerts.get(0).getSeverity());
        int n = alerts.size();
        return "[%s] %d %s alert%s detected".formatted(
                maxSeverity, n, alerts.get(0).getDetectionType(), n != 1 ? "s" : "");
    }

    static String buildHtmlBody(Alert alert) {
        String color = severityColor(alert.getSeverity());

        String extra = "";
        if (alert instanceof TrafficAlert traffic && traffic.getMethodFlags() != null) {
            extra = row("Methods", traffic.getMethodFlags().toDisplayString());
        } else if (alert instanceof DDoSAlert ddos) {
            extra = row("Destination", escapeHtml(ddos.getDestIp()) + ":" + ddos.getDestPort());
        } else if (alert instanceof WebAttackAlert webAttack) {
            extra = row("Layer Triggered", escapeHtml(webAttack.getLayerTriggered()));
        } else if (alert instanceof BruteForceAlert bruteForce) {
            extra = row("Destination", escapeHtml(bruteForce.getDestIp()) + ":" + bruteForce.getDestPort());
        }

        return """
                <!DOCTYPE html>
                <html lang="en">
                <head><meta charset="UTF-8"></head>
                <body style="margin:0;padding:0;background:#f3f4f6;font-family:Arial,sans-serif;">
                  <div style="max-width:600px;margin:32px auto;border-radius:6px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,.12);">
                    <div style="background:%s;padding:24px 28px;">
                      <h2 style="margin:0;color:#fff;font-size:20px;">%s Detected</h2>
                      <span style="display:inline-block;margin-top:8px;background:rgba(255,255,255,.25);color:#fff;padding:2px 12px;border-radius:12px;font-size:12px;font-weight:bold;letter-spacing:.5px;">%s</span>
                    </div>
                    <div style="background:#fff;padding:24px 28px;">
                      <table style="width:100%%;border-collapse:collapse;font-size:14px;">
                        <tbody>
                          %s
                          %s
                          %s
                          %s
                          %s
                        </tbody>
                      </table>
                    </div>
                    <div style="background:#f9fafb;padding:12px 28px;border-top:1px solid #e5e7eb;font-size:11px;color:#9ca3af;text-align:center;">
                      Log Analyzer &mdash; automated security alert
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(
                color,
                alert.getDetectionType(),
                alert.getSeverity(),
                row("Source IP", escapeHtml(alert.getSourceIp())),
                row("Detection Type", alert.getDetectionType().toString()),
                row("Detected At", alert.getDetectedAt().toString()),
                row("Window", (alert.getWindowStart() != null ? alert.getWindowStart() : "")
                        + " &mdash; "
                        + (alert.getWindowEnd() != null ? alert.getWindowEnd() : "")),
                extra
        );
    }

    static String buildBatchHtmlBody(List<Alert> alerts) {
        Severity maxSeverity = alerts.stream()
                .map(Alert::getSeverity)
                .max(Comparator.naturalOrder())
                .orElse(alerts.get(0).getSeverity());
        String color = severityColor(maxSeverity);
        int n = alerts.size();

        Instant earliest = alerts.stream()
                .map(Alert::getDetectedAt)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(alerts.get(0).getDetectedAt());
        Instant latest = alerts.stream()
                .map(Alert::getDetectedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(alerts.get(0).getDetectedAt());

        StringBuilder tableRows = new StringBuilder();
        for (Alert a : alerts) {
            tableRows.append("""
                    <tr style="border-bottom:1px solid #f3f4f6;">
                      <td style="padding:8px 6px;font-size:13px;color:#111827;white-space:nowrap;">%s</td>
                      <td style="padding:8px 6px;font-size:13px;color:#111827;">%s</td>
                      <td style="padding:8px 6px;font-size:13px;">
                        <span style="background:%s;color:#fff;padding:1px 8px;border-radius:10px;font-size:11px;font-weight:bold;">%s</span>
                      </td>
                      <td style="padding:8px 6px;font-size:12px;color:#6b7280;white-space:nowrap;">%s &mdash; %s</td>
                    </tr>
                    """.formatted(
                    a.getDetectedAt() != null ? a.getDetectedAt() : "",
                    escapeHtml(a.getSourceIp()),
                    severityColor(a.getSeverity()),
                    a.getSeverity(),
                    a.getWindowStart() != null ? a.getWindowStart() : "",
                    a.getWindowEnd() != null ? a.getWindowEnd() : ""
            ));
        }

        return """
                <!DOCTYPE html>
                <html lang="en">
                <head><meta charset="UTF-8"></head>
                <body style="margin:0;padding:0;background:#f3f4f6;font-family:Arial,sans-serif;">
                  <div style="max-width:750px;margin:32px auto;border-radius:6px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,.12);">
                    <div style="background:%s;padding:24px 28px;">
                      <h2 style="margin:0;color:#fff;font-size:20px;">%d %s Alert%s Detected</h2>
                      <span style="display:inline-block;margin-top:8px;background:rgba(255,255,255,.25);color:#fff;padding:2px 12px;border-radius:12px;font-size:12px;font-weight:bold;letter-spacing:.5px;">MAX SEVERITY: %s</span>
                    </div>
                    <div style="background:#fff;padding:20px 28px 0;">
                      <p style="margin:0 0 16px;font-size:13px;color:#6b7280;">
                        Events from <strong>%s</strong> to <strong>%s</strong>
                      </p>
                      <table style="width:100%%;border-collapse:collapse;">
                        <thead>
                          <tr style="background:#f9fafb;border-bottom:2px solid #e5e7eb;">
                            <th style="padding:8px 6px;text-align:left;font-size:12px;color:#6b7280;font-weight:600;">Detected At</th>
                            <th style="padding:8px 6px;text-align:left;font-size:12px;color:#6b7280;font-weight:600;">Source IP</th>
                            <th style="padding:8px 6px;text-align:left;font-size:12px;color:#6b7280;font-weight:600;">Severity</th>
                            <th style="padding:8px 6px;text-align:left;font-size:12px;color:#6b7280;font-weight:600;">Window</th>
                          </tr>
                        </thead>
                        <tbody>
                          %s
                        </tbody>
                      </table>
                    </div>
                    <div style="background:#f9fafb;padding:12px 28px;border-top:1px solid #e5e7eb;font-size:11px;color:#9ca3af;text-align:center;">
                      Log Analyzer &mdash; automated security alert
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(
                color, n, alerts.get(0).getDetectionType(), n != 1 ? "s" : "", maxSeverity,
                earliest, latest, tableRows
        );
    }

    static String row(String label, String value) {
        return """
                <tr style="border-bottom:1px solid #f3f4f6;">
                  <td style="padding:10px 0;color:#6b7280;width:38%%;vertical-align:top;">%s</td>
                  <td style="padding:10px 0;color:#111827;font-weight:500;">%s</td>
                </tr>
                """.formatted(label, value);
    }

    static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    static String severityColor(Severity severity) {
        return switch (severity) {
            case CRITICAL -> "#dc2626";
            case HIGH -> "#ea580c";
            case MEDIUM -> "#d97706";
            case LOW -> "#16a34a";
            case NONE -> throw new IllegalStateException("NONE severity should not reach alerting");
        };
    }
}
