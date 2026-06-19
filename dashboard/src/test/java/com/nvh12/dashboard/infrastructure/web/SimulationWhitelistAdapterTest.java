package com.nvh12.dashboard.infrastructure.web;

import com.nvh12.dashboard.infrastructure.config.SimulationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SimulationWhitelistAdapterTest {

    SimulationProperties properties;
    RestClient.Builder restClientBuilder;
    MockRestServiceServer server;
    SimulationWhitelistAdapter adapter;

    @BeforeEach
    void setUp() {
        properties = new SimulationProperties();
        properties.setUrl("http://simulation-test");
        properties.setAdminApiKey("secret-key");

        restClientBuilder = RestClient.builder();
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
        adapter = new SimulationWhitelistAdapter(properties, restClientBuilder.build());
    }

    @Test
    void listWhitelistedIps_returnsIpsFromSimulation() {
        server.expect(requestTo("http://simulation-test/admin/whitelist"))
                .andExpect(header("X-Admin-Key", "secret-key"))
                .andRespond(withSuccess("{\"ips\":[\"1.2.3.4\"]}", MediaType.APPLICATION_JSON));

        assertThat(adapter.listWhitelistedIps()).containsExactly("1.2.3.4");
    }

    @Test
    void replaceWhitelist_putsIpsToSimulation() {
        server.expect(requestTo("http://simulation-test/admin/whitelist"))
                .andExpect(header("X-Admin-Key", "secret-key"))
                .andRespond(withSuccess());

        adapter.replaceWhitelist(List.of("1.2.3.4", "5.6.7.8"));

        server.verify();
    }
}
