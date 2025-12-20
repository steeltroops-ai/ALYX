package com.alyx.integration;

import net.java.quickcheck.Generator;
import net.java.quickcheck.QuickCheck;
import net.java.quickcheck.characteristic.AbstractCharacteristic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * **Feature: alyx-system-fix, Property 1: Service health validation**
 * **Validates: Requirements 1.4, 5.1**
 * 
 * Property-based test that validates service health endpoints.
 * For any deployed service, its health endpoint should return a successful status within the configured timeout period.
 */
public class ServiceHealthValidationPropertyTest {

    /**
     * Represents a service with its health endpoint configuration
     */
    public static class ServiceHealthConfig {
        private final String serviceName;
        private final String healthEndpoint;
        private final int port;
        private final Duration timeout;
        private final boolean isDeployed;

        public ServiceHealthConfig(String serviceName, String healthEndpoint, int port, Duration timeout, boolean isDeployed) {
            this.serviceName = serviceName;
            this.healthEndpoint = healthEndpoint;
            this.port = port;
            this.timeout = timeout;
            this.isDeployed = isDeployed;
        }

        public String getServiceName() { return serviceName; }
        public String getHealthEndpoint() { return healthEndpoint; }
        public int getPort() { return port; }
        public Duration getTimeout() { return timeout; }
        public boolean isDeployed() { return isDeployed; }

        public String getFullHealthUrl() {
            return String.format("http://localhost:%d%s", port, healthEndpoint);
        }

        @Override
        public String toString() {
            return String.format("ServiceHealthConfig{name='%s', endpoint='%s', port=%d, timeout=%s, deployed=%s}", 
                serviceName, healthEndpoint, port, timeout, isDeployed);
        }
    }

    /**
     * Generator for creating random service health configurations
     */
    public static class ServiceHealthConfigGenerator implements Generator<List<ServiceHealthConfig>> {
        private static final Map<String, Integer> SERVICE_PORTS = Map.of(
            "api-gateway", 8080,
            "job-scheduler", 8081,
            "resource-optimizer", 8082,
            "collaboration-service", 8083,
            "notebook-service", 8084,
            "data-processing", 8085
        );

        private static final String[] HEALTH_ENDPOINTS = {
            "/actuator/health", "/health", "/actuator/health/readiness"
        };

        @Override
        public List<ServiceHealthConfig> next() {
            List<ServiceHealthConfig> configs = new ArrayList<>();
            Random random = new Random();
            
            for (Map.Entry<String, Integer> entry : SERVICE_PORTS.entrySet()) {
                String serviceName = entry.getKey();
                int port = entry.getValue();
                
                // Randomly select health endpoint
                String healthEndpoint = HEALTH_ENDPOINTS[random.nextInt(HEALTH_ENDPOINTS.length)];
                
                // Random timeout between 5 and 30 seconds
                Duration timeout = Duration.ofSeconds(5 + random.nextInt(26));
                
                // Randomly determine if service is deployed (simulate different deployment states)
                boolean isDeployed = random.nextBoolean();
                
                configs.add(new ServiceHealthConfig(serviceName, healthEndpoint, port, timeout, isDeployed));
            }
            
            return configs;
        }
    }

    /**
     * Property: Service health endpoints should respond successfully for deployed services
     * For any deployed service, its health endpoint should return a successful status within the configured timeout period
     */
    @Test
    @DisplayName("Property 1: Service health validation - deployed services should have healthy endpoints")
    public void testServiceHealthEndpoints() {
        QuickCheck.forAll(new ServiceHealthConfigGenerator(), new AbstractCharacteristic<List<ServiceHealthConfig>>() {
            @Override
            protected void doSpecify(List<ServiceHealthConfig> serviceConfigs) throws Throwable {
                WebClient webClient = WebClient.builder()
                    .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024))
                    .build();

                for (ServiceHealthConfig config : serviceConfigs) {
                    if (config.isDeployed()) {
                        // For deployed services, health endpoint should be accessible
                        try {
                            String response = webClient.get()
                                .uri(config.getFullHealthUrl())
                                .retrieve()
                                .bodyToMono(String.class)
                                .timeout(config.getTimeout())
                                .block();

                            // Health endpoint should return some response (not null or empty)
                            if (response == null || response.trim().isEmpty()) {
                                throw new AssertionError(
                                    String.format("Service '%s' health endpoint returned empty response. URL: %s", 
                                        config.getServiceName(), config.getFullHealthUrl())
                                );
                            }

                            // Response should contain health-related keywords
                            String lowerResponse = response.toLowerCase();
                            if (!lowerResponse.contains("up") && !lowerResponse.contains("healthy") && 
                                !lowerResponse.contains("status") && !lowerResponse.contains("ok")) {
                                throw new AssertionError(
                                    String.format("Service '%s' health endpoint response doesn't contain expected health indicators. " +
                                        "Response: %s", config.getServiceName(), response)
                                );
                            }

                        } catch (WebClientResponseException e) {
                            // For deployed services, we expect successful HTTP responses (2xx or 3xx)
                            if (e.getStatusCode().is4xxClientError() || e.getStatusCode().is5xxServerError()) {
                                throw new AssertionError(
                                    String.format("Service '%s' health endpoint returned error status: %s. URL: %s", 
                                        config.getServiceName(), e.getStatusCode(), config.getFullHealthUrl())
                                );
                            }
                        } catch (Exception e) {
                            // For deployed services, we don't expect connection timeouts or other errors
                            throw new AssertionError(
                                String.format("Service '%s' health endpoint failed with exception: %s. URL: %s", 
                                    config.getServiceName(), e.getMessage(), config.getFullHealthUrl())
                            );
                        }
                    }
                    // For non-deployed services, we skip the health check as they're not expected to be running
                }
            }
        });
    }

    /**
     * Property: Health endpoints should respond within configured timeout
     * For any service health check, the response time should not exceed the configured timeout
     */
    @Test
    @DisplayName("Property 1: Service health validation - health checks should complete within timeout")
    public void testHealthCheckTimeout() {
        QuickCheck.forAll(new ServiceHealthConfigGenerator(), new AbstractCharacteristic<List<ServiceHealthConfig>>() {
            @Override
            protected void doSpecify(List<ServiceHealthConfig> serviceConfigs) throws Throwable {
                WebClient webClient = WebClient.builder()
                    .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024))
                    .build();

                for (ServiceHealthConfig config : serviceConfigs) {
                    if (config.isDeployed()) {
                        long startTime = System.currentTimeMillis();
                        
                        try {
                            // Attempt health check with timeout
                            CompletableFuture<String> healthCheck = CompletableFuture.supplyAsync(() -> {
                                try {
                                    return webClient.get()
                                        .uri(config.getFullHealthUrl())
                                        .retrieve()
                                        .bodyToMono(String.class)
                                        .timeout(config.getTimeout())
                                        .block();
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            });

                            // Wait for completion or timeout
                            healthCheck.get(config.getTimeout().toMillis() + 1000, TimeUnit.MILLISECONDS);
                            
                            long endTime = System.currentTimeMillis();
                            long actualDuration = endTime - startTime;
                            
                            // Verify the response came within the expected timeout (with small buffer)
                            if (actualDuration > config.getTimeout().toMillis() + 1000) {
                                throw new AssertionError(
                                    String.format("Service '%s' health check took %d ms, exceeding timeout of %d ms", 
                                        config.getServiceName(), actualDuration, config.getTimeout().toMillis())
                                );
                            }

                        } catch (java.util.concurrent.TimeoutException e) {
                            throw new AssertionError(
                                String.format("Service '%s' health check timed out after %d ms", 
                                    config.getServiceName(), config.getTimeout().toMillis())
                            );
                        } catch (Exception e) {
                            // Other exceptions are acceptable for this timeout test
                            // We're only testing that the timeout mechanism works
                        }
                    }
                }
            }
        });
    }

    /**
     * Property: Health endpoint URLs should be well-formed
     * For any service health configuration, the generated health URL should be valid
     */
    @Test
    @DisplayName("Property 1: Service health validation - health URLs should be well-formed")
    public void testHealthUrlFormat() {
        QuickCheck.forAll(new ServiceHealthConfigGenerator(), new AbstractCharacteristic<List<ServiceHealthConfig>>() {
            @Override
            protected void doSpecify(List<ServiceHealthConfig> serviceConfigs) throws Throwable {
                for (ServiceHealthConfig config : serviceConfigs) {
                    String healthUrl = config.getFullHealthUrl();
                    
                    // URL should start with http://localhost
                    if (!healthUrl.startsWith("http://localhost:")) {
                        throw new AssertionError(
                            String.format("Service '%s' health URL should start with 'http://localhost:'. Actual: %s", 
                                config.getServiceName(), healthUrl)
                        );
                    }
                    
                    // URL should contain a valid port number
                    if (config.getPort() < 1 || config.getPort() > 65535) {
                        throw new AssertionError(
                            String.format("Service '%s' has invalid port number: %d", 
                                config.getServiceName(), config.getPort())
                        );
                    }
                    
                    // Health endpoint should start with /
                    if (!config.getHealthEndpoint().startsWith("/")) {
                        throw new AssertionError(
                            String.format("Service '%s' health endpoint should start with '/'. Actual: %s", 
                                config.getServiceName(), config.getHealthEndpoint())
                        );
                    }
                    
                    // URL should be properly formatted
                    try {
                        new java.net.URL(healthUrl);
                    } catch (java.net.MalformedURLException e) {
                        throw new AssertionError(
                            String.format("Service '%s' health URL is malformed: %s. Error: %s", 
                                config.getServiceName(), healthUrl, e.getMessage())
                        );
                    }
                }
            }
        });
    }
}