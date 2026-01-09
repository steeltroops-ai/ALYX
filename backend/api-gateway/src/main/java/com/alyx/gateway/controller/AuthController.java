package com.alyx.gateway.controller;

import com.alyx.gateway.config.EncryptionConfig.AESEncryptionService;
import com.alyx.gateway.dto.UserLoginRequest;
import com.alyx.gateway.dto.UserLoginResponse;
import com.alyx.gateway.dto.UserRegistrationRequest;
import com.alyx.gateway.dto.UserRegistrationResponse;
import com.alyx.gateway.service.AuthService;
import com.alyx.gateway.service.JwtService;
import com.alyx.gateway.service.SecurityAuditService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;

import jakarta.validation.Valid;
import java.time.Instant;
import java.util.HashMap;
import java.util.UUID;
import java.util.Map;

/**
 * Authentication controller for user login and registration
 * 
 * Provides secure authentication endpoints with comprehensive
 * security logging and validation.
 */
@RestController
@RequestMapping("/api/auth")
@Validated
public class AuthController {

    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AESEncryptionService encryptionService;
    private final SecurityAuditService auditService;
    private final AuthService authService;

    public AuthController(JwtService jwtService,
                         PasswordEncoder passwordEncoder,
                         AESEncryptionService encryptionService,
                         SecurityAuditService auditService,
                         AuthService authService) {
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.encryptionService = encryptionService;
        this.auditService = auditService;
        this.authService = authService;
    }

    /**
     * User login endpoint with database authentication
     */
    @PostMapping("/login")
    public ResponseEntity<UserLoginResponse> login(@Valid @RequestBody UserLoginRequest request,
                                                  ServerWebExchange exchange) {
        String clientIp = getClientIpAddress(exchange);
        String userAgent = exchange.getRequest().getHeaders().getFirst("User-Agent");
        
        try {
            // Authenticate user using database-backed authentication
            UserLoginResponse response = authService.authenticateUser(request);
            
            // Log successful authentication with IP and user agent
            auditService.logSecurityEvent("LOGIN_SUCCESS", "/api/auth/login", 
                response.getUser().getId().toString(), 
                String.format("Successful login from IP: %s, User-Agent: %s", clientIp, userAgent));
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            // Validation errors (400 Bad Request)
            auditService.logSecurityEvent("LOGIN_VALIDATION_ERROR", "/api/auth/login", 
                request.getEmail(), String.format("Validation error from IP: %s - %s", clientIp, e.getMessage()));
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "VALIDATION_ERROR");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("timestamp", Instant.now().toString());
            
            return ResponseEntity.badRequest().body(null);
            
        } catch (RuntimeException e) {
            // Authentication failures (401 Unauthorized)
            auditService.logSecurityEvent("LOGIN_FAILED", "/api/auth/login", 
                request.getEmail(), String.format("Authentication failed from IP: %s - %s", clientIp, e.getMessage()));
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "AUTHENTICATION_FAILED");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("timestamp", Instant.now().toString());
            
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
            
        } catch (Exception e) {
            // System errors (500 Internal Server Error)
            auditService.logSecurityEvent("LOGIN_SYSTEM_ERROR", "/api/auth/login", 
                request.getEmail(), String.format("System error from IP: %s - %s", clientIp, e.getMessage()));
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "SYSTEM_ERROR");
            errorResponse.put("message", "Authentication service temporarily unavailable");
            errorResponse.put("timestamp", Instant.now().toString());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    /**
     * User registration endpoint using database-backed authentication
     */
    @PostMapping("/register")
    public ResponseEntity<UserRegistrationResponse> register(@Valid @RequestBody UserRegistrationRequest request,
                                                           ServerWebExchange exchange) {
        String clientIp = getClientIpAddress(exchange);
        
        try {
            // Register user using database-backed authentication service
            UserRegistrationResponse response = authService.registerUser(request);
            
            // Log successful registration with IP
            auditService.logSecurityEvent("REGISTRATION_SUCCESS", "/api/auth/register", 
                response.getUserId().toString(), 
                String.format("New user registered: %s from IP: %s", request.getEmail(), clientIp));
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
            
        } catch (IllegalArgumentException e) {
            // Validation errors (400 Bad Request)
            auditService.logSecurityEvent("REGISTRATION_VALIDATION_ERROR", "/api/auth/register", 
                request.getEmail(), String.format("Validation error from IP: %s - %s", clientIp, e.getMessage()));
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "VALIDATION_ERROR");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("timestamp", Instant.now().toString());
            
            return ResponseEntity.badRequest().body(null);
            
        } catch (RuntimeException e) {
            // Business logic errors (409 Conflict for duplicate email, etc.)
            HttpStatus status = e.getMessage().contains("already exists") ? 
                HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;
                
            auditService.logSecurityEvent("REGISTRATION_FAILED", "/api/auth/register", 
                request.getEmail(), String.format("Registration failed from IP: %s - %s", clientIp, e.getMessage()));
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "REGISTRATION_FAILED");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("timestamp", Instant.now().toString());
            
            return ResponseEntity.status(status).body(null);
            
        } catch (Exception e) {
            // System errors (500 Internal Server Error)
            auditService.logSecurityEvent("REGISTRATION_SYSTEM_ERROR", "/api/auth/register", 
                request.getEmail(), String.format("System error from IP: %s - %s", clientIp, e.getMessage()));
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "SYSTEM_ERROR");
            errorResponse.put("message", "Registration service temporarily unavailable");
            errorResponse.put("timestamp", Instant.now().toString());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    /**
     * Token validation endpoint
     */
    @PostMapping("/validate")
    public ResponseEntity<Map<String, Object>> validateToken(@RequestHeader("Authorization") String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("valid", false, "error", "Invalid token format"));
            }
            
            String token = authHeader.substring(7);
            boolean isValid = jwtService.isTokenValid(token);
            
            if (isValid) {
                UUID userId = jwtService.extractUserId(token);
                String role = jwtService.extractUserRole(token);
                String organization = jwtService.extractOrganization(token);
                
                return ResponseEntity.ok(Map.of(
                    "valid", true,
                    "userId", userId.toString(),
                    "role", role,
                    "organization", organization,
                    "timestamp", Instant.now().toString()
                ));
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("valid", false, "error", "Token expired or invalid"));
            }
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("valid", false, "error", "Token validation failed"));
        }
    }

    /**
     * Logout endpoint (for audit logging)
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(@RequestHeader("Authorization") String authHeader,
                                                    ServerWebExchange exchange) {
        try {
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                UUID userId = jwtService.extractUserId(token);
                
                auditService.logSecurityEvent("LOGOUT", "/api/auth/logout", userId.toString(), 
                    "User logged out from IP: " + getClientIpAddress(exchange));
            }
            
            return ResponseEntity.ok(Map.of(
                "message", "Logged out successfully",
                "timestamp", Instant.now().toString()
            ));
            
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                "message", "Logged out",
                "timestamp", Instant.now().toString()
            ));
        }
    }

    private String getClientIpAddress(ServerWebExchange exchange) {
        String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return exchange.getRequest().getRemoteAddress() != null ? 
            exchange.getRequest().getRemoteAddress().getAddress().getHostAddress() : "unknown";
    }
}