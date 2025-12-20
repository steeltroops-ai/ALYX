package com.alyx.gateway.config;

import com.alyx.gateway.filter.AuthenticationFilter;
import com.alyx.gateway.filter.InputValidationFilter;
import com.alyx.gateway.filter.LoggingFilter;
import com.alyx.gateway.filter.RateLimitingFilter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Gateway routing configuration for ALYX microservices
 * 
 * Defines routes to all backend services with appropriate filters for
 * authentication, rate limiting, circuit breaking, and logging.
 */
@Configuration
public class GatewayConfig {

    private final AuthenticationFilter authenticationFilter;
    private final InputValidationFilter inputValidationFilter;
    private final LoggingFilter loggingFilter;
    private final RateLimitingFilter rateLimitingFilter;

    public GatewayConfig(AuthenticationFilter authenticationFilter,
                        InputValidationFilter inputValidationFilter,
                        LoggingFilter loggingFilter,
                        RateLimitingFilter rateLimitingFilter) {
        this.authenticationFilter = authenticationFilter;
        this.inputValidationFilter = inputValidationFilter;
        this.loggingFilter = loggingFilter;
        this.rateLimitingFilter = rateLimitingFilter;
    }

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
            // Job Scheduler Service Routes
            .route("job-scheduler", r -> r
                .path("/api/jobs/**")
                .filters(f -> f
                    .filter(inputValidationFilter)
                    .filter(loggingFilter)
                    .filter(authenticationFilter)
                    .filter(rateLimitingFilter)
                    .circuitBreaker(config -> config
                        .setName("job-scheduler-cb")
                        .setFallbackUri("forward:/fallback/job-scheduler"))
                    .retry(config -> config
                        .setRetries(3)
                        .setBackoff(java.time.Duration.ofMillis(100), 
                                   java.time.Duration.ofMillis(1000), 2, false)))
                .uri("lb://job-scheduler"))
            
            // Data Router Service Routes
            .route("data-router", r -> r
                .path("/api/data/**")
                .filters(f -> f
                    .filter(inputValidationFilter)
                    .filter(loggingFilter)
                    .filter(authenticationFilter)
                    .filter(rateLimitingFilter)
                    .circuitBreaker(config -> config
                        .setName("data-router-cb")
                        .setFallbackUri("forward:/fallback/data-router"))
                    .retry(config -> config
                        .setRetries(3)
                        .setBackoff(java.time.Duration.ofMillis(100), 
                                   java.time.Duration.ofMillis(1000), 2, false)))
                .uri("lb://data-router"))
            
            // Resource Optimizer Service Routes
            .route("resource-optimizer", r -> r
                .path("/api/resources/**")
                .filters(f -> f
                    .filter(inputValidationFilter)
                    .filter(loggingFilter)
                    .filter(authenticationFilter)
                    .filter(rateLimitingFilter)
                    .circuitBreaker(config -> config
                        .setName("resource-optimizer-cb")
                        .setFallbackUri("forward:/fallback/resource-optimizer"))
                    .retry(config -> config
                        .setRetries(3)
                        .setBackoff(java.time.Duration.ofMillis(100), 
                                   java.time.Duration.ofMillis(1000), 2, false)))
                .uri("lb://resource-optimizer"))
            
            // Collaboration Service Routes
            .route("collaboration-service", r -> r
                .path("/api/collaboration/**")
                .filters(f -> f
                    .filter(inputValidationFilter)
                    .filter(loggingFilter)
                    .filter(authenticationFilter)
                    .filter(rateLimitingFilter)
                    .circuitBreaker(config -> config
                        .setName("collaboration-cb")
                        .setFallbackUri("forward:/fallback/collaboration"))
                    .retry(config -> config
                        .setRetries(2)
                        .setBackoff(java.time.Duration.ofMillis(50), 
                                   java.time.Duration.ofMillis(500), 2, false)))
                .uri("lb://collaboration-service"))
            
            // Notebook Service Routes
            .route("notebook-service", r -> r
                .path("/api/notebooks/**")
                .filters(f -> f
                    .filter(inputValidationFilter)
                    .filter(loggingFilter)
                    .filter(authenticationFilter)
                    .filter(rateLimitingFilter)
                    .circuitBreaker(config -> config
                        .setName("notebook-cb")
                        .setFallbackUri("forward:/fallback/notebook"))
                    .retry(config -> config
                        .setRetries(3)
                        .setBackoff(java.time.Duration.ofMillis(100), 
                                   java.time.Duration.ofMillis(1000), 2, false)))
                .uri("lb://notebook-service"))
            
            // Result Aggregator Service Routes
            .route("result-aggregator", r -> r
                .path("/api/results/**")
                .filters(f -> f
                    .filter(inputValidationFilter)
                    .filter(loggingFilter)
                    .filter(authenticationFilter)
                    .filter(rateLimitingFilter)
                    .circuitBreaker(config -> config
                        .setName("result-aggregator-cb")
                        .setFallbackUri("forward:/fallback/result-aggregator"))
                    .retry(config -> config
                        .setRetries(3)
                        .setBackoff(java.time.Duration.ofMillis(100), 
                                   java.time.Duration.ofMillis(1000), 2, false)))
                .uri("lb://result-aggregator"))
            
            // Quality Monitor Service Routes
            .route("quality-monitor", r -> r
                .path("/api/quality/**")
                .filters(f -> f
                    .filter(inputValidationFilter)
                    .filter(loggingFilter)
                    .filter(authenticationFilter)
                    .filter(rateLimitingFilter)
                    .circuitBreaker(config -> config
                        .setName("quality-monitor-cb")
                        .setFallbackUri("forward:/fallback/quality-monitor"))
                    .retry(config -> config
                        .setRetries(3)
                        .setBackoff(java.time.Duration.ofMillis(100), 
                                   java.time.Duration.ofMillis(1000), 2, false)))
                .uri("lb://quality-monitor"))
            
            // WebSocket routes for real-time collaboration
            .route("websocket-collaboration", r -> r
                .path("/ws/**")
                .filters(f -> f
                    .filter(inputValidationFilter)
                    .filter(loggingFilter)
                    .filter(authenticationFilter))
                .uri("lb://collaboration-service"))
            
            // Health check routes (no authentication required)
            .route("health-checks", r -> r
                .path("/actuator/health/**")
                .filters(f -> f.filter(loggingFilter))
                .uri("lb://job-scheduler"))
            
            .build();
    }
}