package com.alyx.gateway.controller;

import com.alyx.gateway.config.EncryptionConfig.AESEncryptionService;
import com.alyx.gateway.model.UserRole;
import com.alyx.gateway.service.JwtService;
import com.alyx.gateway.service.SecurityAuditService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
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

    public AuthController(JwtService jwtService,
                         PasswordEncoder passwordEncoder,
                         AESEncryptionService encryptionService,
                         SecurityAuditService auditService) {
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.encryptionService = encryptionService;
        this.auditService = auditService;
    }

    /**
     * User login endpoint
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest request,
                                                   ServerWebExchange exchange) {
        String clientIp = getClientIpAddress(exchange);
        String userAgent = exchange.getRequest().getHeaders().getFirst("User-Agent");
        
        try {
            // In a real implementation, this would validate against a user database
            // For demo purposes, we'll use hardcoded users with different roles
            UserCredentials user = validateCredentials(request.getEmail(), request.getPassword());
            
            if (user == null) {
                auditService.logAuthenticationFailure(request.getEmail(), clientIp, userAgent, "Invalid credentials");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid credentials", "timestamp", Instant.now().toString()));
            }
            
            // Generate JWT token with user information
            String token = jwtService.generateToken(
                user.getUserId(),
                user.getRole().getRoleName(),
                user.getOrganization(),
                user.getPermissions()
            );
            
            // Log successful authentication
            auditService.logSecurityEvent("LOGIN_SUCCESS", "/api/auth/login", user.getUserId(), 
                "Successful login from IP: " + clientIp);
            
            Map<String, Object> response = new HashMap<>();
            response.put("token", token);
            response.put("tokenType", "Bearer");
            response.put("expiresIn", 86400); // 24 hours
            response.put("user", Map.of(
                "id", user.getUserId(),
                "email", user.getEmail(),
                "role", user.getRole().getRoleName(),
                "organization", user.getOrganization()
            ));
            response.put("timestamp", Instant.now().toString());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            auditService.logAuthenticationFailure(request.getEmail(), clientIp, userAgent, 
                "Authentication error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Authentication failed", "timestamp", Instant.now().toString()));
        }
    }

    /**
     * User registration endpoint (simplified for demo)
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request,
                                                      ServerWebExchange exchange) {
        String clientIp = getClientIpAddress(exchange);
        
        try {
            // Validate registration request
            if (userExists(request.getEmail())) {
                auditService.logSecurityEvent("REGISTRATION_FAILED", "/api/auth/register", 
                    request.getEmail(), "User already exists");
                return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "User already exists", "timestamp", Instant.now().toString()));
            }
            
            // Hash password
            String hashedPassword = passwordEncoder.encode(request.getPassword());
            
            // Create user (in real implementation, save to database)
            String userId = java.util.UUID.randomUUID().toString();
            UserRole role = UserRole.fromString(request.getRole());
            
            // Log successful registration
            auditService.logSecurityEvent("REGISTRATION_SUCCESS", "/api/auth/register", userId, 
                "New user registered: " + request.getEmail() + " from IP: " + clientIp);
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "User registered successfully");
            response.put("userId", userId);
            response.put("email", request.getEmail());
            response.put("role", role.getRoleName());
            response.put("timestamp", Instant.now().toString());
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
            
        } catch (Exception e) {
            auditService.logSecurityEvent("REGISTRATION_ERROR", "/api/auth/register", 
                request.getEmail(), "Registration error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Registration failed", "timestamp", Instant.now().toString()));
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
                String userId = jwtService.extractUserId(token);
                String role = jwtService.extractUserRole(token);
                String organization = jwtService.extractOrganization(token);
                
                return ResponseEntity.ok(Map.of(
                    "valid", true,
                    "userId", userId,
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
                String userId = jwtService.extractUserId(token);
                
                auditService.logSecurityEvent("LOGOUT", "/api/auth/logout", userId, 
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

    /**
     * Validate user credentials (demo implementation)
     */
    private UserCredentials validateCredentials(String email, String password) {
        // Demo users with different roles
        Map<String, UserCredentials> demoUsers = Map.of(
            "admin@alyx.physics.org", new UserCredentials(
                "admin-001", "admin@alyx.physics.org", 
                passwordEncoder.encode("admin123"), UserRole.ADMIN, "ALYX_PHYSICS",
                List.of("ALL_PERMISSIONS")
            ),
            "physicist@alyx.physics.org", new UserCredentials(
                "physicist-001", "physicist@alyx.physics.org",
                passwordEncoder.encode("physicist123"), UserRole.PHYSICIST, "CERN",
                List.of("SUBMIT_JOBS", "READ_DATA", "CREATE_NOTEBOOKS")
            ),
            "analyst@alyx.physics.org", new UserCredentials(
                "analyst-001", "analyst@alyx.physics.org",
                passwordEncoder.encode("analyst123"), UserRole.ANALYST, "FERMILAB",
                List.of("READ_DATA", "VIEW_RESULTS", "BASIC_VISUALIZATION")
            )
        );
        
        UserCredentials user = demoUsers.get(email);
        if (user != null && passwordEncoder.matches(password, user.getHashedPassword())) {
            return user;
        }
        
        return null;
    }

    /**
     * Check if user exists (demo implementation)
     */
    private boolean userExists(String email) {
        return List.of("admin@alyx.physics.org", "physicist@alyx.physics.org", "analyst@alyx.physics.org")
            .contains(email);
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

    /**
     * Login request DTO
     */
    public static class LoginRequest {
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        private String email;
        
        @NotBlank(message = "Password is required")
        @Size(min = 6, message = "Password must be at least 6 characters")
        private String password;
        
        // Getters and setters
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    /**
     * Registration request DTO
     */
    public static class RegisterRequest {
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        private String email;
        
        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        private String password;
        
        @NotBlank(message = "First name is required")
        private String firstName;
        
        @NotBlank(message = "Last name is required")
        private String lastName;
        
        private String role = "ANALYST"; // Default role
        
        private String organization;
        
        // Getters and setters
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }
        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getOrganization() { return organization; }
        public void setOrganization(String organization) { this.organization = organization; }
    }

    /**
     * User credentials class for demo
     */
    private static class UserCredentials {
        private final String userId;
        private final String email;
        private final String hashedPassword;
        private final UserRole role;
        private final String organization;
        private final List<String> permissions;
        
        public UserCredentials(String userId, String email, String hashedPassword, 
                             UserRole role, String organization, List<String> permissions) {
            this.userId = userId;
            this.email = email;
            this.hashedPassword = hashedPassword;
            this.role = role;
            this.organization = organization;
            this.permissions = permissions;
        }
        
        public String getUserId() { return userId; }
        public String getEmail() { return email; }
        public String getHashedPassword() { return hashedPassword; }
        public UserRole getRole() { return role; }
        public String getOrganization() { return organization; }
        public List<String> getPermissions() { return permissions; }
    }
}