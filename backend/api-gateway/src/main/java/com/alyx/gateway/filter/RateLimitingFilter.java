package com.alyx.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

/**
 * Rate limiting filter using Redis for distributed rate limiting
 * 
 * Implements sliding window rate limiting to prevent abuse and ensure
 * fair resource allocation across users and services.
 */
@Component
public class RateLimitingFilter implements GatewayFilter {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    
    // Rate limiting configuration
    private static final int DEFAULT_REQUESTS_PER_MINUTE = 100;
    private static final int PREMIUM_REQUESTS_PER_MINUTE = 500;
    private static final Duration WINDOW_SIZE = Duration.ofMinutes(1);

    public RateLimitingFilter(ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        
        // Extract user identifier (IP address or user ID from headers)
        String userIdentifier = getUserIdentifier(request);
        String userRole = request.getHeaders().getFirst("X-User-Role");
        
        // Determine rate limit based on user role
        int requestsPerMinute = "PREMIUM".equals(userRole) ? 
            PREMIUM_REQUESTS_PER_MINUTE : DEFAULT_REQUESTS_PER_MINUTE;
        
        return checkRateLimit(userIdentifier, requestsPerMinute)
            .flatMap(allowed -> {
                if (allowed) {
                    return chain.filter(exchange);
                } else {
                    return onRateLimitExceeded(exchange);
                }
            });
    }

    private String getUserIdentifier(ServerHttpRequest request) {
        // Try to get user ID from headers first (if authenticated)
        String userId = request.getHeaders().getFirst("X-User-Id");
        if (userId != null && !userId.isEmpty()) {
            return "user:" + userId;
        }
        
        // Fall back to IP address for unauthenticated requests
        String clientIp = getClientIpAddress(request);
        return "ip:" + clientIp;
    }

    private String getClientIpAddress(ServerHttpRequest request) {
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddress() != null ? 
            request.getRemoteAddress().getAddress().getHostAddress() : "unknown";
    }

    private Mono<Boolean> checkRateLimit(String userIdentifier, int requestsPerMinute) {
        String key = "rate_limit:" + userIdentifier;
        long currentWindow = Instant.now().getEpochSecond() / 60; // 1-minute windows
        String windowKey = key + ":" + currentWindow;
        
        return redisTemplate.opsForValue()
            .increment(windowKey)
            .flatMap(count -> {
                if (count == 1) {
                    // Set expiration for the first request in this window
                    return redisTemplate.expire(windowKey, WINDOW_SIZE)
                        .thenReturn(true);
                } else {
                    // Check if we've exceeded the limit
                    return Mono.just(count <= requestsPerMinute);
                }
            })
            .onErrorReturn(true); // Allow request if Redis is unavailable
    }

    private Mono<Void> onRateLimitExceeded(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().add("Content-Type", "application/json");
        response.getHeaders().add("X-RateLimit-Retry-After", "60");
        
        String body = "{\"error\": \"Rate limit exceeded. Please try again later.\", \"status\": 429}";
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes())));
    }
}