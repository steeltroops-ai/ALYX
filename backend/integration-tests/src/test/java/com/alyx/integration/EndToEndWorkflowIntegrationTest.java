package com.alyx.integration;

import com.alyx.gateway.ApiGatewayApplication;
import com.alyx.jobscheduler.JobSchedulerApplication;
import com.alyx.collaboration.CollaborationServiceApplication;
import com.alyx.dataprocessing.DataProcessingApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Integration tests for complete end-to-end workflows in ALYX system.
 * Tests complete user journeys from job submission to visualization,
 * multi-user collaboration scenarios, and data pipeline validation.
 * 
 * Requirements: 1.1, 2.1, 5.1
 */
@SpringBootTest(
    classes = {
        ApiGatewayApplication.class,
        JobSchedulerApplication.class,
        CollaborationServiceApplication.class,
        DataProcessingApplication.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("integration-test")
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class EndToEndWorkflowIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("timescale/timescaledb-ha:pg15-latest")
            .withDatabaseName("alyx_test")
            .withUsername("test_user")
            .withPassword("test_password")
            .withInitScript("test-schema.sql");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"));

    @LocalServerPort
    private int port;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ObjectMapper objectMapper;

    private String authToken;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.redis.host", redis::getHost);
        registry.add("spring.redis.port", redis::getFirstMappedPort);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @BeforeEach
    void setUp() {
        // Authenticate and get JWT token
        authToken = authenticateUser("physicist@alyx.org", "PHYSICIST");
        
        // Configure WebTestClient with longer timeout for integration tests
        webTestClient = webTestClient.mutate()
                .responseTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * Test complete user journey: Job submission -> Processing -> Visualization
     * Validates Requirements 1.1, 2.1
     */
    @Test
    void testCompleteAnalysisWorkflow() throws Exception {
        // Step 1: Submit analysis job
        String jobSubmissionRequest = """
            {
                "analysisType": "PARTICLE_RECONSTRUCTION",
                "parameters": {
                    "energyRange": {"min": 1000, "max": 5000},
                    "detectorRegions": ["CENTRAL", "FORWARD"],
                    "timeWindow": {"start": "2024-01-01T00:00:00Z", "end": "2024-01-01T01:00:00Z"}
                },
                "priority": "HIGH",
                "resourceRequirements": {
                    "cores": 16,
                    "memoryGB": 32,
                    "estimatedDurationMinutes": 30
                }
            }
            """;

        String jobId = webTestClient.post()
                .uri("/api/jobs/submit")
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(jobSubmissionRequest)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        // Step 2: Monitor job status until completion
        String finalStatus = waitForJobCompletion(jobId, Duration.ofMinutes(5));
        assert "COMPLETED".equals(finalStatus) : "Job should complete successfully";

        // Step 3: Retrieve analysis results
        webTestClient.get()
                .uri("/api/jobs/{jobId}/results", jobId)
                .header("Authorization", "Bearer " + authToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobId").isEqualTo(jobId)
                .jsonPath("$.results").exists()
                .jsonPath("$.results.particleTracks").isArray()
                .jsonPath("$.results.collisionEvents").isArray();

        // Step 4: Request visualization data
        webTestClient.get()
                .uri("/api/visualization/{jobId}/events", jobId)
                .header("Authorization", "Bearer " + authToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.events").isArray()
                .jsonPath("$.events[0].trajectories").exists()
                .jsonPath("$.events[0].detectorHits").exists()
                .jsonPath("$.renderingMetadata").exists();

        // Step 5: Verify data pipeline integrity
        validateDataPipelineIntegrity(jobId);
    }

    /**
     * Test multi-user collaboration scenario
     * Validates Requirement 5.1
     */
    @Test
    void testMultiUserCollaborationWorkflow() throws Exception {
        // Create collaboration session
        String sessionRequest = """
            {
                "sessionName": "Physics Analysis Session",
                "analysisType": "COLLABORATIVE_RECONSTRUCTION",
                "maxParticipants": 5
            }
            """;

        String sessionId = webTestClient.post()
                .uri("/api/collaboration/sessions")
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(sessionRequest)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        // Simulate multiple users joining
        String user2Token = authenticateUser("physicist2@alyx.org", "PHYSICIST");
        String user3Token = authenticateUser("physicist3@alyx.org", "PHYSICIST");

        // User 2 joins session
        webTestClient.post()
                .uri("/api/collaboration/sessions/{sessionId}/join", sessionId)
                .header("Authorization", "Bearer " + user2Token)
                .exchange()
                .expectStatus().isOk();

        // User 3 joins session
        webTestClient.post()
                .uri("/api/collaboration/sessions/{sessionId}/join", sessionId)
                .header("Authorization", "Bearer " + user3Token)
                .exchange()
                .expectStatus().isOk();

        // Verify session state shows all participants
        webTestClient.get()
                .uri("/api/collaboration/sessions/{sessionId}/state", sessionId)
                .header("Authorization", "Bearer " + authToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.participants").isArray()
                .jsonPath("$.participants.length()").isEqualTo(3)
                .jsonPath("$.sessionId").isEqualTo(sessionId);

        // Test concurrent parameter updates
        testConcurrentParameterUpdates(sessionId, authToken, user2Token, user3Token);

        // Test real-time synchronization
        testRealTimeSynchronization(sessionId, authToken, user2Token);
    }

    /**
     * Test data pipeline from ingestion to analysis results
     * Validates complete data flow integrity
     */
    @Test
    void testDataPipelineIntegration() throws Exception {
        // Step 1: Ingest collision event data
        String eventData = generateCollisionEventData();
        
        webTestClient.post()
                .uri("/api/data/events/ingest")
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(eventData)
                .exchange()
                .expectStatus().isAccepted()
                .expectBody()
                .jsonPath("$.batchId").exists()
                .jsonPath("$.eventsCount").isNumber();

        // Step 2: Verify data storage and indexing
        Thread.sleep(2000); // Allow time for async processing

        webTestClient.get()
                .uri("/api/data/events/search?energyMin=1000&energyMax=5000&limit=10")
                .header("Authorization", "Bearer " + authToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.events").isArray()
                .jsonPath("$.totalCount").isNumber()
                .jsonPath("$.queryTime").exists();

        // Step 3: Trigger analysis pipeline
        String pipelineRequest = """
            {
                "pipelineType": "FULL_RECONSTRUCTION",
                "inputFilter": {
                    "energyRange": {"min": 1000, "max": 5000},
                    "timeWindow": "PT1H"
                },
                "outputFormat": "PHYSICS_ANALYSIS"
            }
            """;

        String pipelineId = webTestClient.post()
                .uri("/api/data/pipeline/execute")
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(pipelineRequest)
                .exchange()
                .expectStatus().isAccepted()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        // Step 4: Monitor pipeline execution
        waitForPipelineCompletion(pipelineId, Duration.ofMinutes(3));

        // Step 5: Validate analysis results
        webTestClient.get()
                .uri("/api/data/pipeline/{pipelineId}/results", pipelineId)
                .header("Authorization", "Bearer " + authToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.pipelineId").isEqualTo(pipelineId)
                .jsonPath("$.results.reconstructedTracks").isArray()
                .jsonPath("$.results.qualityMetrics").exists()
                .jsonPath("$.results.processingStatistics").exists();
    }

    /**
     * Test system performance under load
     * Validates Requirements 4.1, 3.4
     */
    @Test
    void testSystemPerformanceUnderLoad() throws Exception {
        int concurrentJobs = 10;
        CompletableFuture<String>[] jobFutures = new CompletableFuture[concurrentJobs];

        // Submit multiple jobs concurrently
        for (int i = 0; i < concurrentJobs; i++) {
            final int jobIndex = i;
            jobFutures[i] = CompletableFuture.supplyAsync(() -> {
                try {
                    String jobRequest = String.format("""
                        {
                            "analysisType": "PERFORMANCE_TEST_%d",
                            "parameters": {
                                "energyRange": {"min": %d, "max": %d},
                                "detectorRegions": ["CENTRAL"]
                            },
                            "priority": "NORMAL"
                        }
                        """, jobIndex, 1000 + jobIndex * 100, 2000 + jobIndex * 100);

                    return webTestClient.post()
                            .uri("/api/jobs/submit")
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(jobRequest)
                            .exchange()
                            .expectStatus().isCreated()
                            .expectBody(String.class)
                            .returnResult()
                            .getResponseBody();
                } catch (Exception e) {
                    throw new RuntimeException("Job submission failed", e);
                }
            });
        }

        // Wait for all jobs to be submitted
        CompletableFuture.allOf(jobFutures).get(30, TimeUnit.SECONDS);

        // Verify system maintains performance
        webTestClient.get()
                .uri("/actuator/metrics/system.cpu.usage")
                .header("Authorization", "Bearer " + authToken)
                .exchange()
                .expectStatus().isOk();

        webTestClient.get()
                .uri("/actuator/metrics/jvm.memory.used")
                .header("Authorization", "Bearer " + authToken)
                .exchange()
                .expectStatus().isOk();
    }

    // Helper methods

    private String authenticateUser(String email, String role) {
        String loginRequest = String.format("""
            {
                "email": "%s",
                "password": "test_password",
                "role": "%s"
            }
            """, email, role);

        return webTestClient.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(loginRequest)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody()
                .get("token")
                .toString();
    }

    private String waitForJobCompletion(String jobId, Duration timeout) throws Exception {
        Instant deadline = Instant.now().plus(timeout);
        
        while (Instant.now().isBefore(deadline)) {
            String status = webTestClient.get()
                    .uri("/api/jobs/{jobId}/status", jobId)
                    .header("Authorization", "Bearer " + authToken)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Map.class)
                    .returnResult()
                    .getResponseBody()
                    .get("status")
                    .toString();

            if ("COMPLETED".equals(status) || "FAILED".equals(status)) {
                return status;
            }

            Thread.sleep(1000);
        }
        
        throw new RuntimeException("Job did not complete within timeout");
    }

    private void waitForPipelineCompletion(String pipelineId, Duration timeout) throws Exception {
        Instant deadline = Instant.now().plus(timeout);
        
        while (Instant.now().isBefore(deadline)) {
            String status = webTestClient.get()
                    .uri("/api/data/pipeline/{pipelineId}/status", pipelineId)
                    .header("Authorization", "Bearer " + authToken)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Map.class)
                    .returnResult()
                    .getResponseBody()
                    .get("status")
                    .toString();

            if ("COMPLETED".equals(status) || "FAILED".equals(status)) {
                return;
            }

            Thread.sleep(500);
        }
        
        throw new RuntimeException("Pipeline did not complete within timeout");
    }

    private void testConcurrentParameterUpdates(String sessionId, String... tokens) {
        // Simulate concurrent parameter updates from multiple users
        CompletableFuture<Void>[] updateFutures = new CompletableFuture[tokens.length];
        
        for (int i = 0; i < tokens.length; i++) {
            final int userIndex = i;
            final String token = tokens[i];
            
            updateFutures[i] = CompletableFuture.runAsync(() -> {
                String updateRequest = String.format("""
                    {
                        "parameterId": "energy_threshold_%d",
                        "value": %d,
                        "userId": "user_%d"
                    }
                    """, userIndex, 1000 + userIndex * 500, userIndex);

                webTestClient.put()
                        .uri("/api/collaboration/sessions/{sessionId}/parameters", sessionId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(updateRequest)
                        .exchange()
                        .expectStatus().isOk();
            });
        }

        // Wait for all updates to complete
        CompletableFuture.allOf(updateFutures).join();
    }

    private void testRealTimeSynchronization(String sessionId, String user1Token, String user2Token) {
        // Test that changes from one user are visible to others in real-time
        String parameterUpdate = """
            {
                "parameterId": "sync_test_param",
                "value": 12345,
                "userId": "user_1"
            }
            """;

        // User 1 makes update
        webTestClient.put()
                .uri("/api/collaboration/sessions/{sessionId}/parameters", sessionId)
                .header("Authorization", "Bearer " + user1Token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(parameterUpdate)
                .exchange()
                .expectStatus().isOk();

        // User 2 should see the update immediately
        webTestClient.get()
                .uri("/api/collaboration/sessions/{sessionId}/parameters/sync_test_param", sessionId)
                .header("Authorization", "Bearer " + user2Token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.value").isEqualTo(12345)
                .jsonPath("$.userId").isEqualTo("user_1");
    }

    private void validateDataPipelineIntegrity(String jobId) {
        // Verify data consistency across the pipeline
        webTestClient.get()
                .uri("/api/jobs/{jobId}/pipeline-integrity", jobId)
                .header("Authorization", "Bearer " + authToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.checksumValid").isEqualTo(true)
                .jsonPath("$.dataConsistency").isEqualTo("VALID")
                .jsonPath("$.pipelineStages").isArray();
    }

    private String generateCollisionEventData() {
        return """
            {
                "events": [
                    {
                        "eventId": "%s",
                        "timestamp": "2024-01-01T12:00:00Z",
                        "centerOfMassEnergy": 2500.0,
                        "collisionVertex": {"x": 0.1, "y": 0.2, "z": 0.0},
                        "detectorHits": [
                            {"detectorId": "CENTRAL_1", "position": {"x": 1.5, "y": 2.1, "z": 0.5}, "energy": 150.0},
                            {"detectorId": "CENTRAL_2", "position": {"x": -1.2, "y": 1.8, "z": -0.3}, "energy": 200.0}
                        ]
                    }
                ]
            }
            """.formatted(UUID.randomUUID().toString());
    }
}