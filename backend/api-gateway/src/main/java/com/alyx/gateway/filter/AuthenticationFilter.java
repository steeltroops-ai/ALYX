package com.alyx.gateway.filter;

import com.alyx.gateway.model.UserRole;
import com.alyx.gateway.model.Permission;
import com.alyx.gateway.service.JwtService;
import com.alyx.gateway.service.SecurityAuditService;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Authentication filter for validating JWT tokens
 * 
 * Validates JWT tokens for protected routes and adds user information
 * to request headers for downstream services.
 */
@Component
public class AuthenticationFilter implements GatewayFilter {

    private final JwtService jwtService;
    private final SecurityAuditService auditService;

    // Routes that don't require authentication
    private static final List<String> OPEN_API_ENDPOINTS = List.of(
        "/api/auth/login",
        "/api/auth/register",
        "/actuator/health",
        "/actuator/prometheus"
    );

    // Role-based access control mapping
    private static final Map<String, UserRole> ENDPOINT_ROLE_REQUIREMENTS = Map.of(
        "/api/admin", UserRole.ADMIN,
        "/api/jobs/submit", UserRole.PHYSICIST,
        "/api/jobs/cancel", UserRole.PHYSICIST,
        "/api/data/write", UserRole.PHYSICIST,
        "/api/data/delete", UserRole.ADMIN,
        "/api/system/config", UserRole.ADMIN,
        "/api/users", UserRole.ADMIN
    );

    // Permission-based access control mapping
    private static final Map<String, Permission> ENDPOINT_PERMISSION_REQUIREMENTS = Map.of(
        "/api/jobs/submit", Permission.SUBMIT_JOBS,
        "/api/jobs/cancel", Permission.CANCEL_JOBS,
        "/api/data/collision-events", Permission.READ_DATA,
        "/api/visualization/advanced", Permission.ADVANCED_VISUALIZATION,
        "/api/notebooks/execute", Permission.EXECUTE_NOTEBOOKS,
        "/api/collaboration/create", Permission.CREATE_SESSIONS,
        "/api/system/metrics", Permission.VIEW_METRICS
    );

    public AuthenticationFilter(JwtService jwtService, SecurityAuditService auditService) {
        this.jwtService = jwtService;
        this.auditService = auditService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        
        // Skip authentication for open endpoints
        if (isSecured.negate().test(request)) {
            return chain.filter(exchange);
        }

        // Check for Authorization header
        if (!request.getHeaders().containsKey("Authorization")) {
            return onError(exchange, "Authorization header is missing", HttpStatus.UNAUTHORIZED);
        }

        String token = request.getHeaders().getOrEmpty("Authorization").get(0);
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        } else {
            return onError(exchange, "Authorization header must be Bearer token", HttpStatus.UNAUTHORIZED);
        }

        try {
            // Validate JWT token
            if (!jwtService.isTokenValid(token)) {
                auditService.logSecurityEvent("INVALID_TOKEN", request.getURI().getPath(), 
                    getClientIpAddress(request), "Token validation failed");
                return onError(exchange, "JWT token is not valid", HttpStatus.UNAUTHORIZED);
            }

            // Extract user information
            String userId = jwtService.extractUserId(token);
            String userRole = jwtService.extractUserRole(token);
            String organization = jwtService.extractOrganization(token);
            UserRole roleEnum = jwtService.extractUserRoleEnum(token);
            
            // Check role-based access control
            if (!hasRequiredRole(request, roleEnum)) {
                auditService.logSecurityEvent("ACCESS_DENIED", request.getURI().getPath(), 
                    userId, "Insufficient role privileges: " + userRole);
                return onError(exchange, "Insufficient privileges for this operation", HttpStatus.FORBIDDEN);
            }
            
            // Check permission-based access control
            if (!hasRequiredPermission(request, token)) {
                auditService.logSecurityEvent("PERMISSION_DENIED", request.getURI().getPath(), 
                    userId, "Missing required permission");
                return onError(exchange, "Missing required permission for this operation", HttpStatus.FORBIDDEN);
            }
            
            // Log successful authentication
            auditService.logSecurityEvent("ACCESS_GRANTED", request.getURI().getPath(), 
                userId, "Successful authentication and authorization");
            
            // Add user information to headers for downstream services
            ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
                .header("X-User-Id", userId)
                .header("X-User-Role", userRole)
                .header("X-User-Organization", organization)
                .header("X-Correlation-ID", generateCorrelationId())
                .build();

            return chain.filter(exchange.mutate().request(modifiedRequest).build());
            
        } catch (Exception e) {
            auditService.logSecurityEvent("AUTH_ERROR", request.getURI().getPath(), 
                "unknown", "Authentication error: " + e.getMessage());
            return onError(exchange, "JWT token validation failed: " + e.getMessage(), HttpStatus.UNAUTHORIZED);
        }
    }

    private final Predicate<ServerHttpRequest> isSecured = request ->
        OPEN_API_ENDPOINTS.stream()
            .noneMatch(uri -> request.getURI().getPath().contains(uri));

    private boolean hasRequiredRole(ServerHttpRequest request, UserRole userRole) {
        String path = request.getURI().getPath();
        
        // Check if endpoint requires specific role
        for (Map.Entry<String, UserRole> entry : ENDPOINT_ROLE_REQUIREMENTS.entrySet()) {
            if (path.startsWith(entry.getKey())) {
                UserRole requiredRole = entry.getValue();
                return userRole.getHierarchyLevel() >= requiredRole.getHierarchyLevel();
            }
        }
        
        // Default: allow if no specific role requirement
        return true;
    }
    
    private boolean hasRequiredPermission(ServerHttpRequest request, String token) {
        String path = request.getURI().getPath();
        HttpMethod method = request.getMethod();
        
        // Check if endpoint requires specific permission
        for (Map.Entry<String, Permission> entry : ENDPOINT_PERMISSION_REQUIREMENTS.entrySet()) {
            if (path.startsWith(entry.getKey())) {
                Permission requiredPermission = entry.getValue();
                return jwtService.hasPermission(token, requiredPermission);
            }
        }
        
        // Additional method-based permission checks
        if (HttpMethod.DELETE.equals(method) && path.startsWith("/api/data/")) {
            return jwtService.hasPermission(token, Permission.DELETE_DATA);
        }
        
        if (HttpMethod.POST.equals(method) && path.startsWith("/api/data/")) {
            return jwtService.hasPermission(token, Permission.WRITE_DATA);
        }
        
        // Default: allow if no specific permission requirement
        return true;
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
    
    private String generateCorrelationId() {
        return java.util.UUID.randomUUID().toString();
    }

    private Mono<Void> onError(ServerWebExchange exchange, String err, HttpStatus httpStatus) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(httpStatus);
        response.getHeaders().add("Content-Type", "application/json");
        response.getHeaders().add("X-Content-Type-Options", "nosniff");
        response.getHeaders().add("X-Frame-Options", "DENY");
        response.getHeaders().add("X-XSS-Protection", "1; mode=block");
        
        String body = String.format("{\"error\": \"%s\", \"status\": %d, \"timestamp\": \"%s\"}", 
            err, httpStatus.value(), java.time.Instant.now().toString());
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes())));
    }
}