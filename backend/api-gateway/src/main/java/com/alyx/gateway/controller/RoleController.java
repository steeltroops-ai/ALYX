package com.alyx.gateway.controller;

import com.alyx.gateway.model.Permission;
import com.alyx.gateway.model.Role;
import com.alyx.gateway.service.JwtService;
import com.alyx.gateway.service.RolePermissionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST Controller for role and permission management
 * 
 * Provides endpoints for role assignment, permission checking,
 * and role-based authorization operations.
 */
@RestController
@RequestMapping("/api/auth/roles")
@CrossOrigin(origins = "*", maxAge = 3600)
public class RoleController {
    
    private static final Logger logger = LoggerFactory.getLogger(RoleController.class);
    
    private final RolePermissionService rolePermissionService;
    private final JwtService jwtService;
    
    @Autowired
    public RoleController(RolePermissionService rolePermissionService, JwtService jwtService) {
        this.rolePermissionService = rolePermissionService;
        this.jwtService = jwtService;
    }
    
    /**
     * Get all available roles
     */
    @GetMapping
    public ResponseEntity<?> getAllRoles(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = extractToken(authHeader);
            if (!jwtService.isTokenValid(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid or expired token"));
            }
            
            UUID userId = jwtService.extractUserId(token);
            
            // Check if user has permission to view roles
            if (!rolePermissionService.hasPermission(userId, Permission.USER_MANAGEMENT)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Insufficient permissions to view roles"));
            }
            
            List<Role> roles = rolePermissionService.getAllRoles();
            return ResponseEntity.ok(Map.of("roles", roles));
            
        } catch (Exception e) {
            logger.error("Error retrieving roles", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to retrieve roles"));
        }
    }
    
    /**
     * Get roles available for a specific organization
     */
    @GetMapping("/organization/{organization}")
    public ResponseEntity<?> getRolesForOrganization(
            @PathVariable String organization,
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = extractToken(authHeader);
            if (!jwtService.isTokenValid(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid or expired token"));
            }
            
            UUID userId = jwtService.extractUserId(token);
            
            // Check if user has permission to view roles
            if (!rolePermissionService.hasPermission(userId, Permission.USER_MANAGEMENT)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Insufficient permissions to view roles"));
            }
            
            List<Role> roles = rolePermissionService.getRolesForOrganization(organization);
            return ResponseEntity.ok(Map.of("roles", roles, "organization", organization));
            
        } catch (Exception e) {
            logger.error("Error retrieving roles for organization: {}", organization, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to retrieve roles for organization"));
        }
    }
    
    /**
     * Change a user's role
     */
    @PutMapping("/assign")
    public ResponseEntity<?> assignRole(
            @RequestBody RoleAssignmentRequest request,
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = extractToken(authHeader);
            if (!jwtService.isTokenValid(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid or expired token"));
            }
            
            UUID assignerUserId = jwtService.extractUserId(token);
            
            // Check if assigner has permission to manage users
            if (!rolePermissionService.hasPermission(assignerUserId, Permission.USER_MANAGEMENT)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Insufficient permissions to assign roles"));
            }
            
            // Check if assigner can assign the specific role
            if (!rolePermissionService.canAssignRole(assignerUserId, request.getRoleName())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Cannot assign role with higher or equal privileges"));
            }
            
            // Perform the role assignment
            boolean success = rolePermissionService.changeUserRole(
                request.getUserId(), 
                request.getRoleName(), 
                assignerUserId
            );
            
            if (success) {
                return ResponseEntity.ok(Map.of(
                    "message", "Role assigned successfully",
                    "userId", request.getUserId(),
                    "newRole", request.getRoleName()
                ));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Role assignment failed"));
            }
            
        } catch (Exception e) {
            logger.error("Error assigning role", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to assign role"));
        }
    }
    
    /**
     * Check if a user has a specific permission
     */
    @GetMapping("/permissions/check")
    public ResponseEntity<?> checkPermission(
            @RequestParam String permission,
            @RequestParam(required = false) UUID userId,
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = extractToken(authHeader);
            if (!jwtService.isTokenValid(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid or expired token"));
            }
            
            UUID requestingUserId = jwtService.extractUserId(token);
            UUID targetUserId = userId != null ? userId : requestingUserId;
            
            // If checking another user's permissions, need USER_MANAGEMENT permission
            if (!targetUserId.equals(requestingUserId) && 
                !rolePermissionService.hasPermission(requestingUserId, Permission.USER_MANAGEMENT)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Insufficient permissions to check other user's permissions"));
            }
            
            boolean hasPermission = rolePermissionService.hasPermission(targetUserId, permission);
            
            return ResponseEntity.ok(Map.of(
                "userId", targetUserId,
                "permission", permission,
                "hasPermission", hasPermission
            ));
            
        } catch (Exception e) {
            logger.error("Error checking permission", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to check permission"));
        }
    }
    
    /**
     * Get all permissions for a user
     */
    @GetMapping("/permissions")
    public ResponseEntity<?> getUserPermissions(
            @RequestParam(required = false) UUID userId,
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = extractToken(authHeader);
            if (!jwtService.isTokenValid(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid or expired token"));
            }
            
            UUID requestingUserId = jwtService.extractUserId(token);
            UUID targetUserId = userId != null ? userId : requestingUserId;
            
            // If checking another user's permissions, need USER_MANAGEMENT permission
            if (!targetUserId.equals(requestingUserId) && 
                !rolePermissionService.hasPermission(requestingUserId, Permission.USER_MANAGEMENT)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Insufficient permissions to view other user's permissions"));
            }
            
            List<String> permissions = rolePermissionService.getUserPermissions(targetUserId);
            
            return ResponseEntity.ok(Map.of(
                "userId", targetUserId,
                "permissions", permissions
            ));
            
        } catch (Exception e) {
            logger.error("Error retrieving user permissions", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to retrieve user permissions"));
        }
    }
    
    /**
     * Get permissions for a specific role
     */
    @GetMapping("/{roleName}/permissions")
    public ResponseEntity<?> getRolePermissions(
            @PathVariable String roleName,
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = extractToken(authHeader);
            if (!jwtService.isTokenValid(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid or expired token"));
            }
            
            UUID userId = jwtService.extractUserId(token);
            
            // Check if user has permission to view role details
            if (!rolePermissionService.hasPermission(userId, Permission.USER_MANAGEMENT)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Insufficient permissions to view role permissions"));
            }
            
            List<String> permissions = rolePermissionService.getPermissionsForRole(roleName);
            
            return ResponseEntity.ok(Map.of(
                "roleName", roleName,
                "permissions", permissions
            ));
            
        } catch (Exception e) {
            logger.error("Error retrieving role permissions for role: {}", roleName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to retrieve role permissions"));
        }
    }
    
    /**
     * Invalidate all tokens for a user (force re-authentication)
     */
    @PostMapping("/invalidate-tokens")
    public ResponseEntity<?> invalidateUserTokens(
            @RequestBody TokenInvalidationRequest request,
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = extractToken(authHeader);
            if (!jwtService.isTokenValid(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid or expired token"));
            }
            
            UUID requestingUserId = jwtService.extractUserId(token);
            
            // Check if user has permission to invalidate tokens
            if (!rolePermissionService.hasPermission(requestingUserId, Permission.USER_MANAGEMENT)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Insufficient permissions to invalidate tokens"));
            }
            
            rolePermissionService.invalidateUserTokens(request.getUserId());
            
            return ResponseEntity.ok(Map.of(
                "message", "User tokens invalidated successfully",
                "userId", request.getUserId()
            ));
            
        } catch (Exception e) {
            logger.error("Error invalidating user tokens", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to invalidate user tokens"));
        }
    }
    
    /**
     * Extract JWT token from Authorization header
     */
    private String extractToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        throw new IllegalArgumentException("Invalid authorization header");
    }
    
    /**
     * Request DTO for role assignment
     */
    public static class RoleAssignmentRequest {
        private UUID userId;
        private String roleName;
        
        public RoleAssignmentRequest() {}
        
        public RoleAssignmentRequest(UUID userId, String roleName) {
            this.userId = userId;
            this.roleName = roleName;
        }
        
        public UUID getUserId() {
            return userId;
        }
        
        public void setUserId(UUID userId) {
            this.userId = userId;
        }
        
        public String getRoleName() {
            return roleName;
        }
        
        public void setRoleName(String roleName) {
            this.roleName = roleName;
        }
    }
    
    /**
     * Request DTO for token invalidation
     */
    public static class TokenInvalidationRequest {
        private UUID userId;
        
        public TokenInvalidationRequest() {}
        
        public TokenInvalidationRequest(UUID userId) {
            this.userId = userId;
        }
        
        public UUID getUserId() {
            return userId;
        }
        
        public void setUserId(UUID userId) {
            this.userId = userId;
        }
    }
}