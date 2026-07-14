package com.ezcloudstore;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class EzCloudStoreApplicationTest {

    @LocalServerPort
    private int port;

    @Test
    void contextLoadsAndHealthEndpointResponds() {
        var body = RestClient.create()
                .get()
                .uri("http://localhost:" + port + "/actuator/health")
                .retrieve()
                .body(String.class);

        assertThat(body).contains("\"status\":\"UP\"");
    }
}
