package io.voltweave.gateway.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest
class GatewaySecurityIntegrationTests {
    @Autowired
    private ApplicationContext context;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToApplicationContext(context).build();
    }

    @Test
    void rejectsAnonymousApiRequestWithTraceableProblem() {
        webTestClient.get()
                .uri("/api/v1/organizations")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectHeader().exists("X-Correlation-Id")
                .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.correlationId").exists();
    }

    @Test
    void allowsAnonymousHealthCheck() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }
}
