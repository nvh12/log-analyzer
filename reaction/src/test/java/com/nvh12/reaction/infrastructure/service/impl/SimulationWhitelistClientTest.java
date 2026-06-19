package com.nvh12.reaction.infrastructure.service.impl;

import com.nvh12.reaction.infrastructure.config.SimulationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withForbiddenRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SimulationWhitelistClientTest {

    SimulationProperties properties;
    RestClient.Builder restClientBuilder;
    MockRestServiceServer server;
    SimulationWhitelistClient client;

    @BeforeEach
    void setUp() {
        properties = new SimulationProperties();
        properties.setUrl("http://simulation-test");
        properties.setAdminApiKey("secret-key");

        restClientBuilder = RestClient.builder();
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
        client = new SimulationWhitelistClient(properties, restClientBuilder);
    }

    @Test
    void isWhitelisted_ipPresentInResponse_returnsTrue() {
        server.expect(requestTo("http://simulation-test/admin/whitelist"))
                .andExpect(header("X-Admin-Key", "secret-key"))
                .andRespond(withSuccess("{\"ips\":[\"1.2.3.4\"]}", MediaType.APPLICATION_JSON));

        assertThat(client.isWhitelisted("1.2.3.4")).isTrue();
    }

    @Test
    void isWhitelisted_ipAbsentFromResponse_returnsFalse() {
        server.expect(requestTo("http://simulation-test/admin/whitelist"))
                .andRespond(withSuccess("{\"ips\":[\"5.6.7.8\"]}", MediaType.APPLICATION_JSON));

        assertThat(client.isWhitelisted("1.2.3.4")).isFalse();
    }

    @Test
    void isWhitelisted_simulationUnreachable_failsOpenAndReturnsFalse() {
        server.expect(requestTo("http://simulation-test/admin/whitelist"))
                .andRespond(withServerError());

        assertThat(client.isWhitelisted("1.2.3.4")).isFalse();
    }

    @Test
    void isWhitelisted_forbidden_failsOpenAndReturnsFalse() {
        server.expect(requestTo("http://simulation-test/admin/whitelist"))
                .andRespond(withForbiddenRequest());

        assertThat(client.isWhitelisted("1.2.3.4")).isFalse();
    }

    @Test
    void isWhitelisted_secondCallWithinTtl_servedFromCacheWithoutNewRequest() {
        server.expect(requestTo("http://simulation-test/admin/whitelist"))
                .andRespond(withSuccess("{\"ips\":[\"1.2.3.4\"]}", MediaType.APPLICATION_JSON));

        assertThat(client.isWhitelisted("1.2.3.4")).isTrue();
        assertThat(client.isWhitelisted("5.6.7.8")).isFalse();

        server.verify();
    }
}
