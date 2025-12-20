package com.alyx.gateway.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Unit tests for Fallback Controller
 */
@WebFluxTest(FallbackController.class)
class FallbackControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void shouldReturnJobSchedulerFallback() {
        webTestClient.get()
            .uri("/fallback/job-scheduler")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
            .expectBody()
            .jsonPath("$.error").isEqualTo("Job Scheduler service is temporarily unavailable")
            .jsonPath("$.service").isEqualTo("job-scheduler")
            .jsonPath("$.fallback").isEqualTo(true);
    }

    @Test
    void shouldReturnDataRouterFallback() {
        webTestClient.get()
            .uri("/fallback/data-router")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
            .expectBody()
            .jsonPath("$.error").isEqualTo("Data Router service is temporarily unavailable")
            .jsonPath("$.service").isEqualTo("data-router")
            .jsonPath("$.fallback").isEqualTo(true);
    }

    @Test
    void shouldReturnResourceOptimizerFallback() {
        webTestClient.get()
            .uri("/fallback/resource-optimizer")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
            .expectBody()
            .jsonPath("$.error").isEqualTo("Resource Optimizer service is temporarily unavailable")
            .jsonPath("$.service").isEqualTo("resource-optimizer")
            .jsonPath("$.fallback").isEqualTo(true);
    }

    @Test
    void shouldReturnCollaborationFallback() {
        webTestClient.post()
            .uri("/fallback/collaboration")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
            .expectBody()
            .jsonPath("$.error").isEqualTo("Collaboration service is temporarily unavailable")
            .jsonPath("$.service").isEqualTo("collaboration-service")
            .jsonPath("$.fallback").isEqualTo(true);
    }

    @Test
    void shouldReturnNotebookFallback() {
        webTestClient.get()
            .uri("/fallback/notebook")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
            .expectBody()
            .jsonPath("$.error").isEqualTo("Notebook service is temporarily unavailable")
            .jsonPath("$.service").isEqualTo("notebook-service")
            .jsonPath("$.fallback").isEqualTo(true);
    }
}