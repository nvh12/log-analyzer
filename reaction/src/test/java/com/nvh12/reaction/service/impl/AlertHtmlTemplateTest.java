package com.nvh12.reaction.service.impl;

import com.nvh12.reaction.service.dto.DetectionType;
import com.nvh12.reaction.service.dto.MethodFlags;
import com.nvh12.reaction.service.dto.Severity;
import com.nvh12.reaction.service.dto.alert.Alert;
import com.nvh12.reaction.service.dto.alert.BruteForceAlert;
import com.nvh12.reaction.service.dto.alert.DDoSAlert;
import com.nvh12.reaction.service.dto.alert.TrafficAlert;
import com.nvh12.reaction.service.dto.alert.WebAttackAlert;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlertHtmlTemplateTest {

    @Test
    void fmtInstant_null_returnsEmptyString() {
        assertThat(AlertHtmlTemplate.fmtInstant(null)).isEmpty();
    }

    @Test
    void fmtInstant_formatsInVietnamTimezone() {
        Instant instant = Instant.parse("2026-01-01T00:00:00Z");

        // Asia/Ho_Chi_Minh is UTC+7, so midnight UTC becomes 07:00 local time
        assertThat(AlertHtmlTemplate.fmtInstant(instant)).startsWith("2026-01-01 07:00:00");
    }

    @Test
    void buildSubject_includesSeverityTypeAndIp() {
        Alert alert = DDoSAlert.builder()
                .detectionType(DetectionType.DDOS).sourceIp("1.2.3.4")
                .severity(Severity.CRITICAL).detectedAt(Instant.now()).build();

        assertThat(AlertHtmlTemplate.buildSubject(alert)).isEqualTo("[CRITICAL] DDOS detected from 1.2.3.4");
    }

    @Test
    void buildBatchSubject_singleAlert_singularWording() {
        List<Alert> alerts = List.of(DDoSAlert.builder()
                .detectionType(DetectionType.DDOS).sourceIp("1.2.3.4")
                .severity(Severity.HIGH).detectedAt(Instant.now()).build());

        assertThat(AlertHtmlTemplate.buildBatchSubject(alerts)).isEqualTo("[HIGH] 1 DDOS alert detected");
    }

    @Test
    void buildBatchSubject_multipleAlerts_usesMaxSeverityAndPluralWording() {
        List<Alert> alerts = List.of(
                DDoSAlert.builder().detectionType(DetectionType.DDOS).sourceIp("1.1.1.1")
                        .severity(Severity.LOW).detectedAt(Instant.now()).build(),
                DDoSAlert.builder().detectionType(DetectionType.DDOS).sourceIp("2.2.2.2")
                        .severity(Severity.CRITICAL).detectedAt(Instant.now()).build());

        assertThat(AlertHtmlTemplate.buildBatchSubject(alerts)).isEqualTo("[CRITICAL] 2 DDOS alerts detected");
    }

    @Test
    void buildHtmlBody_forTrafficAlert_includesMethodFlagsRow() {
        MethodFlags flags = new MethodFlags();
        flags.setZScore(true);
        flags.setSeasonal(true);

        Alert alert = TrafficAlert.builder()
                .detectionType(DetectionType.TRAFFIC).sourceIp("9.9.9.9")
                .severity(Severity.LOW).detectedAt(Instant.now()).methodFlags(flags).build();

        String html = AlertHtmlTemplate.buildHtmlBody(alert);

        assertThat(html).contains("Methods").contains("Z-Score Seasonal");
    }

    @Test
    void buildHtmlBody_forTrafficAlertWithoutMethodFlags_omitsMethodsRow() {
        Alert alert = TrafficAlert.builder()
                .detectionType(DetectionType.TRAFFIC).sourceIp("9.9.9.9")
                .severity(Severity.LOW).detectedAt(Instant.now()).methodFlags(null).build();

        assertThat(AlertHtmlTemplate.buildHtmlBody(alert)).doesNotContain("Methods");
    }

    @Test
    void buildHtmlBody_forDDoSAlert_includesDestinationRow() {
        Alert alert = DDoSAlert.builder()
                .detectionType(DetectionType.DDOS).sourceIp("1.2.3.4")
                .severity(Severity.HIGH).detectedAt(Instant.now())
                .destIp("10.0.0.1").destPort(80).build();

        assertThat(AlertHtmlTemplate.buildHtmlBody(alert)).contains("Destination").contains("10.0.0.1:80");
    }

    @Test
    void buildHtmlBody_forWebAttackAlert_includesLayerTriggeredRow() {
        Alert alert = WebAttackAlert.builder()
                .detectionType(DetectionType.WEB_ATTACK).sourceIp("5.5.5.5")
                .severity(Severity.MEDIUM).detectedAt(Instant.now())
                .layerTriggered("rule_engine").build();

        assertThat(AlertHtmlTemplate.buildHtmlBody(alert)).contains("Layer Triggered").contains("rule_engine");
    }

    @Test
    void buildHtmlBody_forBruteForceAlert_includesDestinationRow() {
        Alert alert = BruteForceAlert.builder()
                .detectionType(DetectionType.BRUTE_FORCE).sourceIp("9.9.9.9")
                .severity(Severity.HIGH).detectedAt(Instant.now())
                .destIp("192.168.1.1").destPort(22).build();

        assertThat(AlertHtmlTemplate.buildHtmlBody(alert)).contains("Destination").contains("192.168.1.1:22");
    }

    @Test
    void buildHtmlBody_escapesSourceIpAndDetectedAtRows() {
        Alert alert = DDoSAlert.builder()
                .detectionType(DetectionType.DDOS).sourceIp("<script>alert(1)</script>")
                .severity(Severity.HIGH).detectedAt(Instant.now()).build();

        String html = AlertHtmlTemplate.buildHtmlBody(alert);

        assertThat(html).doesNotContain("<script>alert(1)</script>");
        assertThat(html).contains("&lt;script&gt;");
    }

    @Test
    void buildBatchHtmlBody_includesAllAlertsAndDateRange() {
        Instant earlier = Instant.parse("2026-01-01T00:00:00Z");
        Instant later = Instant.parse("2026-01-01T01:00:00Z");
        List<Alert> alerts = List.of(
                DDoSAlert.builder().detectionType(DetectionType.DDOS).sourceIp("1.1.1.1")
                        .severity(Severity.LOW).detectedAt(earlier).build(),
                DDoSAlert.builder().detectionType(DetectionType.DDOS).sourceIp("2.2.2.2")
                        .severity(Severity.CRITICAL).detectedAt(later).build());

        String html = AlertHtmlTemplate.buildBatchHtmlBody(alerts);

        assertThat(html).contains("2 DDOS Alerts Detected");
        assertThat(html).contains("MAX SEVERITY: CRITICAL");
        assertThat(html).contains("1.1.1.1").contains("2.2.2.2");
        assertThat(html).contains(AlertHtmlTemplate.fmtInstant(earlier));
        assertThat(html).contains(AlertHtmlTemplate.fmtInstant(later));
    }

    @Test
    void escapeHtml_nullReturnsEmptyString() {
        assertThat(AlertHtmlTemplate.escapeHtml(null)).isEmpty();
    }

    @Test
    void escapeHtml_escapesSpecialCharacters() {
        assertThat(AlertHtmlTemplate.escapeHtml("<a href=\"x\">&'</a>"))
                .isEqualTo("&lt;a href=&quot;x&quot;&gt;&amp;'&lt;/a&gt;");
    }

    @Test
    void severityColor_eachNonNoneSeverityMapsToAColor() {
        assertThat(AlertHtmlTemplate.severityColor(Severity.LOW)).isEqualTo("#16a34a");
        assertThat(AlertHtmlTemplate.severityColor(Severity.MEDIUM)).isEqualTo("#d97706");
        assertThat(AlertHtmlTemplate.severityColor(Severity.HIGH)).isEqualTo("#ea580c");
        assertThat(AlertHtmlTemplate.severityColor(Severity.CRITICAL)).isEqualTo("#dc2626");
    }

    @Test
    void severityColor_none_throwsIllegalState() {
        assertThatThrownBy(() -> AlertHtmlTemplate.severityColor(Severity.NONE))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void row_formatsLabelAndValueAsTableRow() {
        assertThat(AlertHtmlTemplate.row("Label", "Value")).contains("Label").contains("Value").contains("<tr");
    }
}
