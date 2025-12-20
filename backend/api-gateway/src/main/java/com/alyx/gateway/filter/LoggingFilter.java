package com.alyx.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Logging filter for request/response monitoring and tracing
 * 
 * Logs all incoming requests and outgoing responses with timing information,
 * correlation IDs, and user context for monitoring and debugging.
 */
@Component
public class LoggingFilter implements GatewayFilter {

    private static final Logger logger = LoggerFactory.getLogger(LoggingFilter.class);
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String REQUEST_START_TIME = "request_start_time";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        
        // Generate or extract correlation ID
        String correlationId = getOrGenerateCorrelationId(request);
        
        // Add correlation ID to request headers for downstream services
        ServerHttpRequest modifiedRequest = request.mutate()
            .header(CORRELATION_ID_HEADER, correlationId)
            .build();
        
        // Store request start time
        exchange.getAttributes().put(REQUEST_START_TIME, Instant.now());
        
        // Set up MDC for structured logging
        MDC.put("correlationId", correlationId);
        MDC.put("method", request.getMethod().toString());
        MDC.put("path", request.getPath().toString());
        MDC.put("userAgent", request.getHeaders().getFirst("User-Agent"));
        
        // Extract user information if available
        String userId = request.getHeaders().getFirst("X-User-Id");
        if (userId != null) {
            MDC.put("userId", userId);
        }
        
        // Log incoming request
        logIncomingRequest(request, correlationId);
        
        return chain.filter(exchange.mutate().request(modifiedRequest).build())
            .doOnSuccess(aVoid -> logOutgoingResponse(exchange, correlationId))
            .doOnError(throwable -> logErrorResponse(exchange, correlationId, throwable))
            .doFinally(signalType -> MDC.clear());
    }

    private String getOrGenerateCorrelationId(ServerHttpRequest request) {
        String correlationId = request.getHeaders().getFirst(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isEmpty()) {
            correlationId = UUID.randomUUID().toString();
        }
        return correlationId;
    }

    private void logIncomingRequest(ServerHttpRequest request, String correlationId) {
        logger.info("Incoming request: {} {} - Correlation ID: {} - User-Agent: {} - Remote Address: {}",
            request.getMethod(),
            request.getURI(),
            correlationId,
            request.getHeaders().getFirst("User-Agent"),
            getClientIpAddress(request));
    }

    private void logOutgoingResponse(ServerWebExchange exchange, String correlationId) {
        ServerHttpResponse response = exchange.getResponse();
        Instant startTime = exchange.getAttribute(REQUEST_START_TIME);
        long duration = startTime != null ? 
            Instant.now().toEpochMilli() - startTime.toEpochMilli() : 0;
        
        logger.info("Outgoing response: {} - Status: {} - Duration: {}ms - Correlation ID: {}",
            exchange.getRequest().getURI(),
            response.getStatusCode(),
            duration,
            correlationId);
        
        // Add response headers for monitoring
        response.getHeaders().add(CORRELATION_ID_HEADER, correlationId);
        response.getHeaders().add("X-Response-Time", duration + "ms");
    }

    private void logErrorResponse(ServerWebExchange exchange, String correlationId, Throwable throwable) {
        Instant startTime = exchange.getAttribute(REQUEST_START_TIME);
        long duration = startTime != null ? 
            Instant.now().toEpochMilli() - startTime.toEpochMilli() : 0;
        
        logger.error("Error processing request: {} - Duration: {}ms - Correlation ID: {} - Error: {}",
            exchange.getRequest().getURI(),
            duration,
            correlationId,
            throwable.getMessage(),
            throwable);
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
}