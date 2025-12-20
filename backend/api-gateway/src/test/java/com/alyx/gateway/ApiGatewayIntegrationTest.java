package com.alyx.gateway;

import com.alyx.gateway.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Integration tests for API Gateway
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ApiGatewayIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private JwtService jwtService;

    @Test
    void shouldAllowAccessToHealthEndpoint() {
        webTestClient.get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus().isOk();
    }

    @Test
    void shouldRejectUnauthenticatedRequestToProtectedEndpoint() {
        webTestClient.get()
            .uri("/api/jobs/status")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldAcceptValidJwtToken() {
        // Given
        String token = jwtService.generateToken("test-user", "PHYSICIST");

        // When/Then
        webTestClient.get()
            .uri("/api/jobs/status")
            .header("Authorization", "Bearer " + token)
            .exchange()
            .expectStatus().isNotFound(); // 404 because service is not running, but auth passed
    }

    @Test
    void shouldRejectInvalidJwtToken() {
        webTestClient.get()
            .uri("/api/jobs/status")
            .header("Authorization", "Bearer invalid-token")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldRejectMissingAuthorizationHeader() {
        webTestClient.get()
            .uri("/api/jobs/status")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldAddCorrelationIdToResponse() {
        String token = jwtService.generateToken("test-user", "PHYSICIST");

        webTestClient.get()
            .uri("/api/jobs/status")
            .header("Authorization", "Bearer " + token)
            .exchange()
            .expectHeader().exists("X-Correlation-ID");
    }
}