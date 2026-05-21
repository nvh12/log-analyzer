package com.nvh12.reaction.service.impl;

import com.nvh12.reaction.service.dto.Severity;
import com.nvh12.reaction.service.dto.alert.Alert;
import com.nvh12.reaction.service.dto.alert.BruteForceAlert;
import com.nvh12.reaction.service.dto.alert.DDoSAlert;
import com.nvh12.reaction.service.dto.alert.TrafficAlert;
import com.nvh12.reaction.service.dto.alert.WebAttackAlert;

final class AlertHtmlTemplate {

    private AlertHtmlTemplate() {}

    static String buildSubject(Alert alert) {
        return "[%s] %s detected from %s".formatted(
                alert.getSeverity(),
                alert.getDetectionType(),
                alert.getSourceIp()
        );
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
                row("Source IP",      escapeHtml(alert.getSourceIp())),
                row("Detection Type", alert.getDetectionType().toString()),
                row("Detected At",    alert.getDetectedAt().toString()),
                row("Window",         alert.getWindowStart() + " &mdash; " + alert.getWindowEnd()),
                extra
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
            case HIGH     -> "#ea580c";
            case MEDIUM   -> "#d97706";
            case LOW      -> "#16a34a";
        };
    }
}
