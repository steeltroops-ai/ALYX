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
 * **Feature: alyx-system-fix, Property 3: Service accessibility**
 * **Validates: Requirements 1.5**
 * 
 * Property-based test that validates service accessibility.
 * For any successfully deployed service, all its configured endpoints should be reachable and respond appropriately.
 */
public class ServiceAccessibilityPropertyTest {

    /**
     * Represents a service endpoint configuration
     */
    public static class ServiceEndpoint {
        private final String serviceName;
        private final String endpoint;
        private final int port;
        private final String httpMethod;
        private final boolean requiresAuth;
        private final boolean isDeployed;
        private final Duration timeout;

        public ServiceEndpoint(String serviceName, String endpoint, int port, String httpMethod, 
                             boolean requiresAuth, boolean isDeployed, Duration timeout) {
            this.serviceName = serviceName;
            this.endpoint = endpoint;
            this.port = port;
            this.httpMethod = httpMethod;
            this.requiresAuth = requiresAuth;
            this.isDeployed = isDeployed;
            this.timeout = timeout;
        }

        public String getServiceName() { return serviceName; }
        public String getEndpoint() { return endpoint; }
        public int getPort() { return port; }
        public String getHttpMethod() { return httpMethod; }
        public boolean requiresAuth() { return requiresAuth; }
        public boolean isDeployed() { return isDeployed; }
        public Duration getTimeout() { return timeout; }

        public String getFullUrl() {
            return String.format("http://localhost:%d%s", port, endpoint);
        }

        @Override
        public String toString() {
            return String.format("ServiceEndpoint{service='%s', endpoint='%s', port=%d, method='%s', auth=%s, deployed=%s}", 
                serviceName, endpoint, port, httpMethod, requiresAuth, isDeployed);
        }
    }

    /**
     * Generator for creating random service endpoint configurations
     */
    public static class ServiceEndpointGenerator implements Generator<List<ServiceEndpoint>> {
        private static final Map<String, Integer> SERVICE_PORTS = Map.of(
            "api-gateway", 8080,
            "job-scheduler", 8081,
            "resource-optimizer", 8082,
            "collaboration-service", 8083,
            "notebook-service", 8084,
            "data-processing", 8085
        );

        private static final Map<String, String[]> SERVICE_ENDPOINTS = Map.of(
            "api-gateway", new String[]{"/actuator/health", "/actuator/info", "/api/v1/status"},
            "job-scheduler", new String[]{"/actuator/health", "/api/v1/jobs", "/api/v1/jobs/status"},
            "resource-optimizer", new String[]{"/actuator/health", "/api/v1/resources", "/api/v1/optimization"},
            "collaboration-service", new String[]{"/actuator/health", "/api/v1/sessions", "/ws/collaboration"},
            "notebook-service", new String[]{"/actuator/health", "/api/v1/notebooks", "/api/v1/execute"},
            "data-processing", new String[]{"/actuator/health", "/api/v1/events", "/api/v1/analysis"}
        );

        private static final String[] HTTP_METHODS = {"GET", "POST", "PUT", "DELETE"};

        @Override
        public List<ServiceEndpoint> next() {
            List<ServiceEndpoint> endpoints = new ArrayList<>();
            Random random = new Random();
            
            for (Map.Entry<String, Integer> serviceEntry : SERVICE_PORTS.entrySet()) {
                String serviceName = serviceEntry.getKey();
                int port = serviceEntry.getValue();
                String[] serviceEndpoints = SERVICE_ENDPOINTS.get(serviceName);
                
                // Generate endpoints for this service
                for (String endpoint : serviceEndpoints) {
                    // Random HTTP method (but health endpoints are always GET)
                    String method = endpoint.contains("health") ? "GET" : 
                                  HTTP_METHODS[random.nextInt(HTTP_METHODS.length)];
                    
                    // Random auth requirement (health endpoints typically don't require auth)
                    boolean requiresAuth = !endpoint.contains("health") && random.nextBoolean();
                    
                    // Random deployment status
                    boolean isDeployed = random.nextBoolean();
                    
                    // Random timeout between 5 and 30 seconds
                    Duration timeout = Duration.ofSeconds(5 + random.nextInt(26));
                    
                    endpoints.add(new ServiceEndpoint(serviceName, endpoint, port, method, 
                                                    requiresAuth, isDeployed, timeout));
                }
            }
            
            return endpoints;
        }
    }

    /**
     * Property: Service endpoints should be accessible for deployed services
     * For any successfully deployed service, all its configured endpoints should be reachable
     */
    @Test
    @DisplayName("Property 3: Service accessibility - deployed service endpoints should be reachable")
    public void testServiceEndpointAccessibility() {
        QuickCheck.forAll(new ServiceEndpointGenerator(), new AbstractCharacteristic<List<ServiceEndpoint>>() {
            @Override
            protected void doSpecify(List<ServiceEndpoint> endpoints) throws Throwable {
                WebClient webClient = WebClient.builder()
                    .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024))
                    .build();

                for (ServiceEndpoint endpoint : endpoints) {
                    if (endpoint.isDeployed()) {
                        try {
                            // Test endpoint accessibility based on HTTP method
                            WebClient.ResponseSpec responseSpec;
                            
                            switch (endpoint.getHttpMethod().toUpperCase()) {
                                case "GET":
                                    responseSpec = webClient.get()
                                        .uri(endpoint.getFullUrl())
                                        .retrieve();
                                    break;
                                case "POST":
                                    responseSpec = webClient.post()
                                        .uri(endpoint.getFullUrl())
                                        .retrieve();
                                    break;
                                case "PUT":
                                    responseSpec = webClient.put()
                                        .uri(endpoint.getFullUrl())
                                        .retrieve();
                                    break;
                                case "DELETE":
                                    responseSpec = webClient.delete()
                                        .uri(endpoint.getFullUrl())
                                        .retrieve();
                                    break;
                                default:
                                    throw new AssertionError("Unsupported HTTP method: " + endpoint.getHttpMethod());
                            }

                            String response = responseSpec
                                .bodyToMono(String.class)
                                .timeout(endpoint.getTimeout())
                                .block();

                            // For deployed services, we should get some response
                            // (even if it's an error response, it means the endpoint is accessible)
                            
                        } catch (WebClientResponseException e) {
                            // Check if the error is due to accessibility issues vs expected business logic errors
                            if (e.getStatusCode().is4xxClientError()) {
                                // 4xx errors are acceptable - they indicate the endpoint is accessible
                                // but may require authentication, proper parameters, etc.
                                if (endpoint.requiresAuth() && (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403)) {
                                    // Expected authentication error for protected endpoints
                                    continue;
                                }
                                if (e.getStatusCode().value() == 400 || e.getStatusCode().value() == 404 || e.getStatusCode().value() == 405) {
                                    // Bad request, not found, or method not allowed are acceptable
                                    // They indicate the service is running and accessible
                                    continue;
                                }
                            }
                            
                            if (e.getStatusCode().is5xxServerError()) {
                                // 5xx errors indicate the service is accessible but has internal issues
                                // For this accessibility test, we consider this as accessible
                                continue;
                            }
                            
                            throw new AssertionError(
                                String.format("Service '%s' endpoint '%s' returned unexpected error: %s %s", 
                                    endpoint.getServiceName(), endpoint.getEndpoint(), 
                                    e.getStatusCode(), e.getMessage())
                            );
                            
                        } catch (java.util.concurrent.TimeoutException e) {
                            throw new AssertionError(
                                String.format("Service '%s' endpoint '%s' timed out after %d ms", 
                                    endpoint.getServiceName(), endpoint.getEndpoint(), 
                                    endpoint.getTimeout().toMillis())
                            );
                            
                        } catch (Exception e) {
                            // Connection refused, unknown host, etc. indicate accessibility issues
                            if (e.getMessage().contains("Connection refused") || 
                                e.getMessage().contains("ConnectException") ||
                                e.getMessage().contains("UnknownHostException")) {
                                throw new AssertionError(
                                    String.format("Service '%s' endpoint '%s' is not accessible: %s", 
                                        endpoint.getServiceName(), endpoint.getEndpoint(), e.getMessage())
                                );
                            }
                            
                            // Other exceptions might be acceptable depending on the endpoint
                            throw new AssertionError(
                                String.format("Service '%s' endpoint '%s' failed with unexpected error: %s", 
                                    endpoint.getServiceName(), endpoint.getEndpoint(), e.getMessage())
                            );
                        }
                    }
                    // For non-deployed services, we skip the accessibility check
                }
            }
        });
    }

    /**
     * Property: Service endpoints should respond within reasonable time
     * For any accessible endpoint, the response time should be reasonable for the service type
     */
    @Test
    @DisplayName("Property 3: Service accessibility - endpoints should respond within reasonable time")
    public void testServiceResponseTime() {
        QuickCheck.forAll(new ServiceEndpointGenerator(), new AbstractCharacteristic<List<ServiceEndpoint>>() {
            @Override
            protected void doSpecify(List<ServiceEndpoint> endpoints) throws Throwable {
                WebClient webClient = WebClient.builder()
                    .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024))
                    .build();

                for (ServiceEndpoint endpoint : endpoints) {
                    if (endpoint.isDeployed()) {
                        long startTime = System.currentTimeMillis();
                        
                        try {
                            CompletableFuture<String> request = CompletableFuture.supplyAsync(() -> {
                                try {
                                    return webClient.get()
                                        .uri(endpoint.getFullUrl())
                                        .retrieve()
                                        .bodyToMono(String.class)
                                        .timeout(endpoint.getTimeout())
                                        .block();
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            });

                            // Wait for completion with timeout
                            request.get(endpoint.getTimeout().toMillis() + 1000, TimeUnit.MILLISECONDS);
                            
                            long endTime = System.currentTimeMillis();
                            long responseTime = endTime - startTime;
                            
                            // Define reasonable response time limits based on endpoint type
                            long maxResponseTime;
                            if (endpoint.getEndpoint().contains("health")) {
                                maxResponseTime = 5000; // Health checks should be fast (5 seconds)
                            } else if (endpoint.getEndpoint().contains("info") || endpoint.getEndpoint().contains("status")) {
                                maxResponseTime = 10000; // Info endpoints (10 seconds)
                            } else {
                                maxResponseTime = endpoint.getTimeout().toMillis(); // Use configured timeout
                            }
                            
                            if (responseTime > maxResponseTime) {
                                throw new AssertionError(
                                    String.format("Service '%s' endpoint '%s' took %d ms, exceeding maximum of %d ms", 
                                        endpoint.getServiceName(), endpoint.getEndpoint(), 
                                        responseTime, maxResponseTime)
                                );
                            }
                            
                        } catch (java.util.concurrent.TimeoutException e) {
                            throw new AssertionError(
                                String.format("Service '%s' endpoint '%s' timed out", 
                                    endpoint.getServiceName(), endpoint.getEndpoint())
                            );
                        } catch (Exception e) {
                            // Other exceptions are acceptable for this response time test
                            // We're only testing that accessible endpoints respond in reasonable time
                        }
                    }
                }
            }
        });
    }

    /**
     * Property: Service endpoint URLs should be well-formed and valid
     * For any service endpoint configuration, the generated URL should be valid and properly formatted
     */
    @Test
    @DisplayName("Property 3: Service accessibility - endpoint URLs should be well-formed")
    public void testEndpointUrlFormat() {
        QuickCheck.forAll(new ServiceEndpointGenerator(), new AbstractCharacteristic<List<ServiceEndpoint>>() {
            @Override
            protected void doSpecify(List<ServiceEndpoint> endpoints) throws Throwable {
                for (ServiceEndpoint endpoint : endpoints) {
                    String fullUrl = endpoint.getFullUrl();
                    
                    // URL should start with http://localhost
                    if (!fullUrl.startsWith("http://localhost:")) {
                        throw new AssertionError(
                            String.format("Service '%s' endpoint URL should start with 'http://localhost:'. Actual: %s", 
                                endpoint.getServiceName(), fullUrl)
                        );
                    }
                    
                    // Port should be valid
                    if (endpoint.getPort() < 1 || endpoint.getPort() > 65535) {
                        throw new AssertionError(
                            String.format("Service '%s' has invalid port: %d", 
                                endpoint.getServiceName(), endpoint.getPort())
                        );
                    }
                    
                    // Endpoint path should start with /
                    if (!endpoint.getEndpoint().startsWith("/")) {
                        throw new AssertionError(
                            String.format("Service '%s' endpoint path should start with '/'. Actual: %s", 
                                endpoint.getServiceName(), endpoint.getEndpoint())
                        );
                    }
                    
                    // HTTP method should be valid
                    String method = endpoint.getHttpMethod().toUpperCase();
                    if (!Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS").contains(method)) {
                        throw new AssertionError(
                            String.format("Service '%s' has invalid HTTP method: %s", 
                                endpoint.getServiceName(), endpoint.getHttpMethod())
                        );
                    }
                    
                    // URL should be properly formatted
                    try {
                        new java.net.URL(fullUrl);
                    } catch (java.net.MalformedURLException e) {
                        throw new AssertionError(
                            String.format("Service '%s' endpoint URL is malformed: %s. Error: %s", 
                                endpoint.getServiceName(), fullUrl, e.getMessage())
                        );
                    }
                    
                    // Timeout should be reasonable
                    if (endpoint.getTimeout().toMillis() < 1000 || endpoint.getTimeout().toMillis() > 300000) {
                        throw new AssertionError(
                            String.format("Service '%s' has unreasonable timeout: %d ms (should be between 1-300 seconds)", 
                                endpoint.getServiceName(), endpoint.getTimeout().toMillis())
                        );
                    }
                }
            }
        });
    }
}