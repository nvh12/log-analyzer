package com.nvh12.reaction.infrastructure.service.impl;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.nvh12.reaction.infrastructure.config.AlertProperties;
import com.nvh12.reaction.service.dto.DetectionType;
import com.nvh12.reaction.service.dto.MethodFlags;
import com.nvh12.reaction.service.dto.Severity;
import com.nvh12.reaction.service.dto.alert.BruteForceAlert;
import com.nvh12.reaction.service.dto.alert.DDoSAlert;
import com.nvh12.reaction.service.dto.alert.TrafficAlert;
import com.nvh12.reaction.service.dto.alert.WebAttackAlert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.Flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DiscordAlertServiceTest {

    private static final String WEBHOOK_URL = "https://discord.com/api/webhooks/123/token";

    @Mock HttpClient httpClient;
    @Mock AlertProperties alertProperties;
    @Mock AlertProperties.Discord discord;
    @Mock HttpResponse<String> httpResponse;

    DiscordAlertService service;

    @BeforeEach
    void setUp() throws Exception {
        when(alertProperties.getDiscord()).thenReturn(discord);
        when(discord.getWebhookUrl()).thenReturn(WEBHOOK_URL);
        lenient().doReturn(httpResponse).when(httpClient).send(any(), any());
        lenient().when(httpResponse.statusCode()).thenReturn(200);
        service = new DiscordAlertService(alertProperties, new ObjectMapper(), httpClient);
    }

    private HttpRequest captureRequest() throws Exception {
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any());
        return captor.getValue();
    }

    private JsonNode captureEmbed() throws Exception {
        return new ObjectMapper().readTree(extractBody(captureRequest())).get("embeds").get(0);
    }

    private static String extractBody(HttpRequest request) {
        return request.bodyPublisher()
                .map(publisher -> {
                    var result = new StringBuilder();
                    publisher.subscribe(new Flow.Subscriber<>() {
                        @Override public void onSubscribe(Flow.Subscription s) { s.request(Long.MAX_VALUE); }
                        @Override public void onNext(ByteBuffer b) { result.append(StandardCharsets.UTF_8.decode(b)); }
                        @Override public void onError(Throwable t) {}
                        @Override public void onComplete() {}
                    });
                    return result.toString();
                })
                .orElse("");
    }

    @Test
    void alert_postsToCorrectUrlWithJsonContentType() throws Exception {
        service.alert(DDoSAlert.builder()
                .detectionType(DetectionType.DDOS).sourceIp("1.2.3.4")
                .severity(Severity.CRITICAL).detectedAt(Instant.now()).build());

        HttpRequest req = captureRequest();
        assertThat(req.uri().toString()).isEqualTo(WEBHOOK_URL);
        assertThat(req.headers().firstValue("Content-Type")).hasValue("application/json");
    }

    @Test
    void alert_forDDoSAlert_includesDestinationInPayload() throws Exception {
        service.alert(DDoSAlert.builder()
                .detectionType(DetectionType.DDOS).sourceIp("1.2.3.4")
                .severity(Severity.HIGH).detectedAt(Instant.now())
                .destIp("10.0.0.1").destPort(80).build());

        assertThat(captureEmbed().get("fields").toString())
                .contains("Destination").contains("10.0.0.1:80");
    }

    @Test
    void alert_forWebAttackAlert_includesLayerTriggeredInPayload() throws Exception {
        service.alert(WebAttackAlert.builder()
                .detectionType(DetectionType.WEB_ATTACK).sourceIp("5.5.5.5")
                .severity(Severity.MEDIUM).detectedAt(Instant.now())
                .layerTriggered("rule_engine").build());

        assertThat(captureEmbed().get("fields").toString())
                .contains("Layer Triggered").contains("rule_engine");
    }

    @Test
    void alert_forTrafficAlert_includesOnlyActiveMethodsInPayload() throws Exception {
        MethodFlags flags = new MethodFlags();
        flags.setZScore(true);
        flags.setSeasonal(true);

        service.alert(TrafficAlert.builder()
                .detectionType(DetectionType.TRAFFIC).sourceIp("2.2.2.2")
                .severity(Severity.LOW).detectedAt(Instant.now())
                .methodFlags(flags).build());

        String fields = captureEmbed().get("fields").toString();
        assertThat(fields).contains("Z-Score").contains("Seasonal");
        assertThat(fields).doesNotContain("IQR").doesNotContain("EMA");
    }

    @Test
    void alert_forBruteForceAlert_includesDestinationInPayload() throws Exception {
        service.alert(BruteForceAlert.builder()
                .detectionType(DetectionType.BRUTE_FORCE).sourceIp("9.9.9.9")
                .severity(Severity.HIGH).detectedAt(Instant.now())
                .destIp("192.168.1.1").destPort(22).build());

        assertThat(captureEmbed().get("fields").toString())
                .contains("Destination").contains("192.168.1.1:22");
    }

    @Test
    void alert_whenResponseIsNotOk_doesNotThrow() throws Exception {
        when(httpResponse.statusCode()).thenReturn(429);

        assertDoesNotThrow(() -> service.alert(DDoSAlert.builder()
                .detectionType(DetectionType.DDOS).sourceIp("1.1.1.1")
                .severity(Severity.LOW).detectedAt(Instant.now()).build()));
    }

    @Test
    void alert_whenHttpClientThrows_doesNotRethrow() throws Exception {
        doThrow(new IOException("connection refused")).when(httpClient).send(any(), any());

        assertDoesNotThrow(() -> service.alert(DDoSAlert.builder()
                .detectionType(DetectionType.DDOS).sourceIp("1.1.1.1")
                .severity(Severity.LOW).detectedAt(Instant.now()).build()));
    }
}
