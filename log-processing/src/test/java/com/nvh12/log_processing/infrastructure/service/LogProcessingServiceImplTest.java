package com.nvh12.log_processing.infrastructure.service;

import com.nvh12.log_processing.domain.model.NormalizedFlowRecord;
import com.nvh12.log_processing.domain.model.NormalizedLog;
import com.nvh12.log_processing.domain.model.ProcessingResult;
import com.nvh12.log_processing.domain.model.RawLog;
import com.nvh12.log_processing.infrastructure.config.LogProcessingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LogProcessingServiceImplTest {

    private LogProcessingServiceImpl service;

    private static final LogProcessingProperties PROPERTIES = new LogProcessingProperties(
            10, 1, 10000, 40, 10000, 2000, 3, 30000L, 5000L,
            new LogProcessingProperties.ThreadPool(4, 12, 50, 30),
            new LogProcessingProperties.Validation(45, 2048, 512));

    @BeforeEach
    void setUp() {
        service = new LogProcessingServiceImpl(new ObjectMapper(), PROPERTIES);
    }

    private RawLog rawLog(String source, String message) {
        return RawLog.builder()
                .id(UUID.randomUUID().toString())
                .source(source)
                .rawMessage(message)
                .receivedAt(Instant.now())
                .build();
    }

    // ── HTTP / CLF ───────────────────────────────────────────────────────────

    @Test
    void parsesMinimalClfEntry() {
        String clf = "192.168.1.1 - - [01/Jul/1995:00:00:01 +0000] \"GET /index.html HTTP/1.0\" 200 1234";

        ProcessingResult result = service.process(rawLog("http", clf));

        assertThat(result).isInstanceOf(ProcessingResult.Http.class);
        NormalizedLog log = ((ProcessingResult.Http) result).log();
        assertThat(log.ip()).isEqualTo("192.168.1.1");
        assertThat(log.method()).isEqualTo("GET");
        assertThat(log.url()).isEqualTo("/index.html");
        assertThat(log.statusCode()).isEqualTo(200);
        assertThat(log.responseSize()).isEqualTo(1234);
        assertThat(log.queryString()).isEmpty();
        assertThat(log.userAgent()).isNull();
        assertThat(log.referer()).isNull();
    }

    @Test
    void parsesCombinedLogFormatWithRefererAndUserAgent() {
        String combined = "10.0.0.2 - - [15/Mar/2023:12:00:00 +0000] \"POST /api/login HTTP/1.1\" 401 512 \"https://example.com\" \"Mozilla/5.0\"";

        NormalizedLog log = ((ProcessingResult.Http) service.process(rawLog("http", combined))).log();

        assertThat(log.ip()).isEqualTo("10.0.0.2");
        assertThat(log.statusCode()).isEqualTo(401);
        assertThat(log.referer()).isEqualTo("https://example.com");
        assertThat(log.userAgent()).isEqualTo("Mozilla/5.0");
    }

    @Test
    void extractsQueryStringFromUrl() {
        String clf = "1.2.3.4 - - [01/Jul/1995:00:00:01 +0000] \"GET /search?q=test&page=2 HTTP/1.0\" 200 500";

        NormalizedLog log = ((ProcessingResult.Http) service.process(rawLog("http", clf))).log();

        assertThat(log.url()).isEqualTo("/search");
        assertThat(log.queryString()).isEqualTo("q=test&page=2");
    }

    @Test
    void handlesHyphenResponseSizeAsZero() {
        String clf = "1.2.3.4 - - [01/Jul/1995:00:00:01 +0000] \"HEAD / HTTP/1.0\" 304 -";

        NormalizedLog log = ((ProcessingResult.Http) service.process(rawLog("http", clf))).log();

        assertThat(log.responseSize()).isEqualTo(0);
    }

    @Test
    void nonFlowSourceDefaultsToHttpParsing() {
        String clf = "1.2.3.4 - - [01/Jul/1995:00:00:01 +0000] \"GET / HTTP/1.0\" 200 100";

        assertThat(service.process(rawLog("unknown-source", clf)))
                .isInstanceOf(ProcessingResult.Http.class);
    }

    @Test
    void throwsOnUnparsableClfEntry() {
        assertThatThrownBy(() -> service.process(rawLog("http", "not a valid log line")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unparseable CLF entry");
    }

    // ── Flow ─────────────────────────────────────────────────────────────────

    @Test
    void parsesValidFlowJson() {
        String json = """
                {
                  "timestamp": 1688000000.0,
                  "source_ip": "10.0.0.1",
                  "dest_ip": "192.168.1.100",
                  "source_port": 54321,
                  "dest_port": 443,
                  "features": {
                    "Flow Bytes/s": 1500.5,
                    "Total Bwd Packets": 42.0
                  }
                }
                """;

        ProcessingResult result = service.process(rawLog("flow", json));

        assertThat(result).isInstanceOf(ProcessingResult.Flow.class);
        NormalizedFlowRecord rec = ((ProcessingResult.Flow) result).record();
        assertThat(rec.sourceIp()).isEqualTo("10.0.0.1");
        assertThat(rec.destIp()).isEqualTo("192.168.1.100");
        assertThat(rec.sourcePort()).isEqualTo(54321);
        assertThat(rec.destPort()).isEqualTo(443);
        assertThat(rec.timestamp()).isEqualTo(1688000000.0);
        assertThat(rec.features())
                .containsEntry("Flow Bytes/s", 1500.5)
                .containsEntry("Total Bwd Packets", 42.0);
    }

    @Test
    void flowMissingOptionalIpFieldsDefaultToEmpty() {
        String json = """
                {
                  "timestamp": 0.0,
                  "source_port": 0,
                  "dest_port": 0,
                  "features": {}
                }
                """;

        NormalizedFlowRecord rec = ((ProcessingResult.Flow) service.process(rawLog("flow", json))).record();

        assertThat(rec.sourceIp()).isEmpty();
        assertThat(rec.destIp()).isEmpty();
    }

    @Test
    void flowFeaturesWithZeroValuesArePreserved() {
        String json = """
                {
                  "timestamp": 0.0,
                  "source_port": 0,
                  "dest_port": 0,
                  "features": {"zeroed_feature": 0.0, "normal_feature": 99.9}
                }
                """;

        NormalizedFlowRecord rec = ((ProcessingResult.Flow) service.process(rawLog("flow", json))).record();

        assertThat(rec.features())
                .containsEntry("zeroed_feature", 0.0)
                .containsEntry("normal_feature", 99.9);
    }

    @Test
    void throwsOnInvalidFlowJson() {
        assertThatThrownBy(() -> service.process(rawLog("flow", "definitely not json")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to parse flow record");
    }
}
