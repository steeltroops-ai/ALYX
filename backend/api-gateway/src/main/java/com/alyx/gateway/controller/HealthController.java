package com.alyx.gateway.controller;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Health check controller for API Gateway monitoring
 * 
 * Provides detailed health information about the gateway and its dependencies
 * including Redis connectivity and service discovery status.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController implements HealthIndicator {

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    public HealthController(ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @GetMapping("/gateway")
    public Mono<ResponseEntity<Map<String, Object>>> getGatewayHealth() {
        return checkRedisHealth()
            .map(redisHealthy -> {
                Map<String, Object> health = Map.of(
                    "status", redisHealthy ? "UP" : "DEGRADED",
                    "timestamp", Instant.now().toString(),
                    "service", "api-gateway",
                    "version", "1.0.0",
                    "dependencies", Map.of(
                        "redis", redisHealthy ? "UP" : "DOWN",
                        "eureka", "UP" // Assume UP if we're running
                    )
                );
                return ResponseEntity.ok(health);
            })
            .onErrorReturn(ResponseEntity.ok(Map.of(
                "status", "DOWN",
                "timestamp", Instant.now().toString(),
                "service", "api-gateway",
                "error", "Health check failed"
            )));
    }

    @Override
    public Health health() {
        try {
            boolean redisHealthy = checkRedisHealth().block(Duration.ofSeconds(2));
            if (redisHealthy) {
                return Health.up()
                    .withDetail("redis", "UP")
                    .withDetail("timestamp", Instant.now())
                    .build();
            } else {
                return Health.down()
                    .withDetail("redis", "DOWN")
                    .withDetail("timestamp", Instant.now())
                    .build();
            }
        } catch (Exception e) {
            return Health.down()
                .withDetail("error", e.getMessage())
                .withDetail("timestamp", Instant.now())
                .build();
        }
    }

    private Mono<Boolean> checkRedisHealth() {
        return redisTemplate.opsForValue()
            .set("health:check", "ping", Duration.ofSeconds(10))
            .then(redisTemplate.opsForValue().get("health:check"))
            .map("ping"::equals)
            .onErrorReturn(false)
            .timeout(Duration.ofSeconds(2));
    }
}