package com.alyx.gateway.filter;

import com.alyx.gateway.model.Permission;
import com.alyx.gateway.service.JwtService;
import com.alyx.gateway.service.RolePermissionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Reactive authorization filter for role-based access control
 * 
 * Validates JWT tokens and checks user permissions for protected resources
 * based on role-based authorization rules in a reactive WebFlux environment.
 */
@Component
public class ReactiveAuthorizationFilter implements WebFilter {
    
    private static final Logger logger = LoggerFactory.getLogger(ReactiveAuthorizationFilter.class);
    
    private final JwtService jwtService;
    private final RolePermissionService rolePermissionService;
    private final ObjectMapper objectMapper;
    
    // Endpoint permission mappings
    private static final Map<String, Permission> ENDPOINT_PERMISSIONS;
    
    static {
        Map<String, Permission> permissions = new HashMap<>();
        permissions.put("/api/data/", Permission.READ_DATA);
        permissions.put("/api/jobs/submit", Permission.SUBMIT_JOBS);
        permissions.put("/api/jobs/cancel", Permission.CANCEL_JOBS);
        permissions.put("/api/jobs/modify", Permission.MODIFY_JOBS);
        permissions.put("/api/visualization/", Permission.BASIC_VISUALIZATION);
        permissions.put("/api/visualization/advanced", Permission.ADVANCED_VISUALIZATION);
        permissions.put("/api/collaboration/create", Permission.CREATE_SESSIONS);
        permissions.put("/api/collaboration/manage", Permission.MANAGE_SESSIONS);
        permissions.put("/api/notebooks/create", Permission.CREATE_NOTEBOOKS);
        permissions.put("/api/notebooks/execute", Permission.EXECUTE_NOTEBOOKS);
        permissions.put("/api/auth/roles", Permission.USER_MANAGEMENT);
        permissions.put("/api/system/config", Permission.SYSTEM_CONFIG);
        permissions.put("/api/metrics/", Permission.VIEW_METRICS);
        ENDPOINT_PERMISSIONS = Collections.unmodifiableMap(permissions);
    }
    
    @Autowired
    public ReactiveAuthorizationFilter(JwtService jwtService, 
                                     RolePermissionService rolePermissionService,
                                     ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.rolePermissionService = rolePermissionService;
        this.objectMapper = objectMapper;
    }
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String requestPath = request.getURI().getPath();
        String method = request.getMethod().name();
        
        logger.debug("Processing authorization for {} {}", method, requestPath);
        
        // Skip authorization for public endpoints
        if (isPublicEndpoint(requestPath, method)) {
            return chain.filter(exchange);
        }
        
        return Mono.fromCallable(() -> {
            // Extract JWT token from Authorization header
            String authHeader = request.getHeaders().getFirst("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                throw new UnauthorizedException("Missing or invalid authorization header");
            }
            
            String token = authHeader.substring(7);
            
            // Validate token
            if (!jwtService.isTokenValid(token)) {
                throw new UnauthorizedException("Invalid or expired token");
            }
            
            // Extract user information from token
            UUID userId = jwtService.extractUserId(token);
            
            // Check if endpoint requires specific permission
            Permission requiredPermission = getRequiredPermission(requestPath, method);
            if (requiredPermission != null) {
                if (!rolePermissionService.hasPermission(userId, requiredPermission)) {
                    throw new ForbiddenException("Insufficient permissions for this resource");
                }
            }
            
            logger.debug("Authorization successful for user {} accessing {} {}", userId, method, requestPath);
            
            return userId;
        })
        .flatMap(userId -> {
            // Add user information to request attributes for downstream use
            ServerHttpRequest mutatedRequest = request.mutate()
                .header("X-User-Id", userId.toString())
                .header("X-User-Email", jwtService.extractUserEmail(request.getHeaders().getFirst("Authorization").substring(7)))
                .header("X-User-Role", jwtService.extractUserRole(request.getHeaders().getFirst("Authorization").substring(7)))
                .build();
            
            ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();
            return chain.filter(mutatedExchange);
        })
        .onErrorResume(UnauthorizedException.class, ex -> {
            logger.warn("Unauthorized access attempt for {} {}: {}", method, requestPath, ex.getMessage());
            return sendErrorResponse(exchange.getResponse(), HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", ex.getMessage());
        })
        .onErrorResume(ForbiddenException.class, ex -> {
            logger.warn("Forbidden access attempt for {} {}: {}", method, requestPath, ex.getMessage());
            return sendErrorResponse(exchange.getResponse(), HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage());
        })
        .onErrorResume(Exception.class, ex -> {
            logger.error("Authorization error for {} {}", method, requestPath, ex);
            return sendErrorResponse(exchange.getResponse(), HttpStatus.UNAUTHORIZED, "AUTHORIZATION_ERROR", "Authorization failed");
        });
    }
    
    /**
     * Check if the endpoint is public and doesn't require authorization
     */
    private boolean isPublicEndpoint(String path, String method) {
        // Public endpoints that don't require authentication
        return path.equals("/api/auth/login") ||
               path.equals("/api/auth/register") ||
               path.equals("/api/health") ||
               path.equals("/api/health/ready") ||
               path.equals("/api/health/live") ||
               path.startsWith("/api/public/") ||
               (path.equals("/api/data/public") && "GET".equals(method)) ||
               path.startsWith("/actuator/") ||
               path.equals("/favicon.ico") ||
               path.startsWith("/static/") ||
               path.startsWith("/fallback/");
    }
    
    /**
     * Get the required permission for a specific endpoint and method
     */
    private Permission getRequiredPermission(String path, String method) {
        // Check exact matches first
        for (Map.Entry<String, Permission> entry : ENDPOINT_PERMISSIONS.entrySet()) {
            if (path.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        
        // Method-specific permission checks
        if ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) {
            if (path.startsWith("/api/data/")) {
                return Permission.WRITE_DATA;
            }
            if (path.startsWith("/api/notebooks/")) {
                return Permission.CREATE_NOTEBOOKS;
            }
        }
        
        if ("DELETE".equals(method)) {
            if (path.startsWith("/api/data/")) {
                return Permission.DELETE_DATA;
            }
        }
        
        if ("GET".equals(method)) {
            if (path.startsWith("/api/data/")) {
                return Permission.READ_DATA;
            }
            if (path.startsWith("/api/jobs/")) {
                return Permission.VIEW_RESULTS;
            }
            if (path.startsWith("/api/visualization/")) {
                return Permission.BASIC_VISUALIZATION;
            }
        }
        
        // Default: no specific permission required (but still need valid token)
        return null;
    }
    
    /**
     * Send error response
     */
    private Mono<Void> sendErrorResponse(ServerHttpResponse response, HttpStatus status, String code, String message) {
        response.setStatusCode(status);
        response.getHeaders().add("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        
        Map<String, Object> errorResponse = Map.of(
            "error", Map.of(
                "code", code,
                "message", message,
                "timestamp", java.time.Instant.now().toString()
            )
        );
        
        try {
            String jsonResponse = objectMapper.writeValueAsString(errorResponse);
            DataBuffer buffer = response.bufferFactory().wrap(jsonResponse.getBytes(StandardCharsets.UTF_8));
            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            logger.error("Error writing error response", e);
            return response.setComplete();
        }
    }
    
    /**
     * Custom exception for unauthorized access
     */
    private static class UnauthorizedException extends RuntimeException {
        public UnauthorizedException(String message) {
            super(message);
        }
    }
    
    /**
     * Custom exception for forbidden access
     */
    private static class ForbiddenException extends RuntimeException {
        public ForbiddenException(String message) {
            super(message);
        }
    }
}