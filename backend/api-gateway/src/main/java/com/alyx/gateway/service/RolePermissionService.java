package com.alyx.gateway.service;

import com.alyx.gateway.model.Permission;
import com.alyx.gateway.model.Role;
import com.alyx.gateway.model.User;
import com.alyx.gateway.model.UserRole;
import com.alyx.gateway.repository.RoleRepository;
import com.alyx.gateway.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for role-based authorization and permission management
 * 
 * Provides role assignment validation, permission checking for protected resources,
 * and role change handling with token invalidation capabilities.
 */
@Service
@Transactional
public class RolePermissionService {
    
    private static final Logger logger = LoggerFactory.getLogger(RolePermissionService.class);
    
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final SecurityAuditService auditService;
    
    // Token invalidation tracking - in production, use Redis or database
    private final Set<String> invalidatedTokens = ConcurrentHashMap.newKeySet();
    
    // Organization-based role assignment rules
    private static final Map<String, List<String>> ORGANIZATION_ALLOWED_ROLES = Map.of(
        "CERN", List.of("PHYSICIST", "ANALYST", "GUEST"),
        "FERMILAB", List.of("PHYSICIST", "ANALYST", "GUEST"),
        "DESY", List.of("PHYSICIST", "ANALYST", "GUEST"),
        "KEK", List.of("PHYSICIST", "ANALYST", "GUEST"),
        "ALYX_PHYSICS", List.of("ADMIN", "PHYSICIST", "ANALYST", "GUEST"),
        "UNIVERSITY", List.of("ANALYST", "GUEST")
    );
    
    @Autowired
    public RolePermissionService(RoleRepository roleRepository, 
                               UserRepository userRepository,
                               SecurityAuditService auditService) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }
    
    /**
     * Validate role assignment based on organization and business rules
     * 
     * @param targetRoleName the role to be assigned
     * @param organization the user's organization
     * @param assignerUserId the ID of the user making the assignment (for audit)
     * @return true if the role assignment is valid, false otherwise
     */
    public boolean validateRoleAssignment(String targetRoleName, String organization, UUID assignerUserId) {
        logger.debug("Validating role assignment: {} for organization: {}", targetRoleName, organization);
        
        try {
            // Check if target role exists
            Optional<Role> targetRoleOpt = roleRepository.findByName(targetRoleName);
            if (targetRoleOpt.isEmpty()) {
                logger.warn("Role assignment validation failed - role not found: {}", targetRoleName);
                auditService.logSecurityEvent("ROLE_ASSIGNMENT_VALIDATION_FAILED", 
                    "/api/auth/role-assignment", 
                    assignerUserId != null ? assignerUserId.toString() : "system", 
                    "Target role not found: " + targetRoleName);
                return false;
            }
            
            Role targetRole = targetRoleOpt.get();
            
            // Check organization-based role restrictions
            if (organization != null && ORGANIZATION_ALLOWED_ROLES.containsKey(organization.toUpperCase())) {
                List<String> allowedRoles = ORGANIZATION_ALLOWED_ROLES.get(organization.toUpperCase());
                if (!allowedRoles.contains(targetRoleName.toUpperCase())) {
                    logger.warn("Role assignment validation failed - role {} not allowed for organization: {}", 
                        targetRoleName, organization);
                    auditService.logSecurityEvent("ROLE_ASSIGNMENT_VALIDATION_FAILED", 
                        "/api/auth/role-assignment", 
                        assignerUserId != null ? assignerUserId.toString() : "system", 
                        String.format("Role %s not allowed for organization %s", targetRoleName, organization));
                    return false;
                }
            } else if (organization != null) {
                // For unknown organizations, only allow ANALYST and GUEST roles
                UserRole targetUserRole = UserRole.fromString(targetRoleName);
                if (targetUserRole == UserRole.ADMIN || targetUserRole == UserRole.PHYSICIST) {
                    logger.warn("Role assignment validation failed - high privilege role {} not allowed for unknown organization: {}", 
                        targetRoleName, organization);
                    auditService.logSecurityEvent("ROLE_ASSIGNMENT_VALIDATION_FAILED", 
                        "/api/auth/role-assignment", 
                        assignerUserId != null ? assignerUserId.toString() : "system", 
                        String.format("High privilege role %s not allowed for unknown organization %s", targetRoleName, organization));
                    return false;
                }
            }
            
            // Additional business rule: ADMIN role can only be assigned by existing ADMIN
            if (UserRole.fromString(targetRoleName) == UserRole.ADMIN && assignerUserId != null) {
                Optional<User> assignerOpt = userRepository.findById(assignerUserId);
                if (assignerOpt.isEmpty() || 
                    !UserRole.fromString(assignerOpt.get().getRole().getName()).equals(UserRole.ADMIN)) {
                    logger.warn("Role assignment validation failed - only ADMIN can assign ADMIN role");
                    auditService.logSecurityEvent("ROLE_ASSIGNMENT_VALIDATION_FAILED", 
                        "/api/auth/role-assignment", 
                        assignerUserId.toString(), 
                        "Only ADMIN can assign ADMIN role");
                    return false;
                }
            }
            
            logger.debug("Role assignment validation successful: {} for organization: {}", targetRoleName, organization);
            return true;
            
        } catch (Exception e) {
            logger.error("Error during role assignment validation", e);
            auditService.logSecurityEvent("ROLE_ASSIGNMENT_VALIDATION_ERROR", 
                "/api/auth/role-assignment", 
                assignerUserId != null ? assignerUserId.toString() : "system", 
                "Validation error: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if a user has a specific permission for protected resources
     * 
     * @param userId the user's ID
     * @param permission the permission to check
     * @return true if the user has the permission, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean hasPermission(UUID userId, Permission permission) {
        if (userId == null || permission == null) {
            return false;
        }
        
        try {
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty()) {
                logger.warn("Permission check failed - user not found: {}", userId);
                return false;
            }
            
            User user = userOpt.get();
            
            // Check if user is active
            if (!user.getIsActive() || user.isAccountLocked()) {
                logger.warn("Permission check failed - user inactive or locked: {}", userId);
                return false;
            }
            
            // Check role-based permission
            boolean hasPermission = user.hasPermission(permission);
            
            logger.debug("Permission check for user {} and permission {}: {}", 
                userId, permission.name(), hasPermission);
            
            return hasPermission;
            
        } catch (Exception e) {
            logger.error("Error during permission check for user {} and permission {}", 
                userId, permission.name(), e);
            return false;
        }
    }
    
    /**
     * Check if a user has a specific permission by permission name
     * 
     * @param userId the user's ID
     * @param permissionName the permission name to check
     * @return true if the user has the permission, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean hasPermission(UUID userId, String permissionName) {
        try {
            Permission permission = Permission.valueOf(permissionName.toUpperCase());
            return hasPermission(userId, permission);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid permission name: {}", permissionName);
            return false;
        }
    }
    
    /**
     * Check if a user has any of the specified permissions
     * 
     * @param userId the user's ID
     * @param permissions the list of permissions to check
     * @return true if the user has at least one of the permissions, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean hasAnyPermission(UUID userId, List<Permission> permissions) {
        if (userId == null || permissions == null || permissions.isEmpty()) {
            return false;
        }
        
        return permissions.stream().anyMatch(permission -> hasPermission(userId, permission));
    }
    
    /**
     * Check if a user has all of the specified permissions
     * 
     * @param userId the user's ID
     * @param permissions the list of permissions to check
     * @return true if the user has all of the permissions, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean hasAllPermissions(UUID userId, List<Permission> permissions) {
        if (userId == null || permissions == null || permissions.isEmpty()) {
            return false;
        }
        
        return permissions.stream().allMatch(permission -> hasPermission(userId, permission));
    }
    
    /**
     * Change a user's role and invalidate existing tokens
     * 
     * @param userId the user's ID
     * @param newRoleName the new role name
     * @param changerUserId the ID of the user making the change
     * @return true if the role change was successful, false otherwise
     */
    public boolean changeUserRole(UUID userId, String newRoleName, UUID changerUserId) {
        logger.info("Attempting to change role for user {} to {}", userId, newRoleName);
        
        try {
            // Find the user
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty()) {
                logger.warn("Role change failed - user not found: {}", userId);
                auditService.logSecurityEvent("ROLE_CHANGE_FAILED", 
                    "/api/auth/role-change", 
                    changerUserId != null ? changerUserId.toString() : "system", 
                    "User not found: " + userId);
                return false;
            }
            
            User user = userOpt.get();
            String oldRoleName = user.getRole().getName();
            
            // Validate the role assignment
            if (!validateRoleAssignment(newRoleName, user.getOrganization(), changerUserId)) {
                logger.warn("Role change failed - validation failed for user {} to role {}", userId, newRoleName);
                return false;
            }
            
            // Find the new role
            Optional<Role> newRoleOpt = roleRepository.findByName(newRoleName);
            if (newRoleOpt.isEmpty()) {
                logger.warn("Role change failed - new role not found: {}", newRoleName);
                auditService.logSecurityEvent("ROLE_CHANGE_FAILED", 
                    "/api/auth/role-change", 
                    changerUserId != null ? changerUserId.toString() : "system", 
                    "New role not found: " + newRoleName);
                return false;
            }
            
            Role newRole = newRoleOpt.get();
            
            // Update user's role
            user.setRole(newRole);
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);
            
            // Invalidate user's existing tokens
            invalidateUserTokens(userId);
            
            logger.info("Role change successful for user {} from {} to {}", userId, oldRoleName, newRoleName);
            
            // Log the role change
            auditService.logSecurityEvent("ROLE_CHANGE_SUCCESS", 
                "/api/auth/role-change", 
                changerUserId != null ? changerUserId.toString() : "system", 
                String.format("User %s role changed from %s to %s", userId, oldRoleName, newRoleName));
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error during role change for user {} to role {}", userId, newRoleName, e);
            auditService.logSecurityEvent("ROLE_CHANGE_ERROR", 
                "/api/auth/role-change", 
                changerUserId != null ? changerUserId.toString() : "system", 
                "Role change error: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Invalidate all tokens for a specific user
     * In production, this should be implemented using Redis or database-based token blacklisting
     * 
     * @param userId the user's ID
     */
    public void invalidateUserTokens(UUID userId) {
        logger.info("Invalidating all tokens for user: {}", userId);
        
        // In a real implementation, you would:
        // 1. Add user ID to a blacklist in Redis with expiration
        // 2. Or maintain a database table of invalidated tokens
        // 3. Or use a token versioning system
        
        // For this implementation, we'll use a simple in-memory set
        // This is not suitable for production use in a distributed system
        String userIdString = userId.toString();
        invalidatedTokens.add(userIdString);
        
        // Log the token invalidation
        auditService.logSecurityEvent("TOKENS_INVALIDATED", 
            "/api/auth/token-invalidation", 
            userId.toString(), 
            "All tokens invalidated for user due to role change");
        
        logger.debug("Tokens invalidated for user: {}", userId);
    }
    
    /**
     * Check if a token has been invalidated
     * 
     * @param userId the user ID from the token
     * @return true if the token is invalidated, false otherwise
     */
    public boolean isTokenInvalidated(UUID userId) {
        if (userId == null) {
            return true;
        }
        
        return invalidatedTokens.contains(userId.toString());
    }
    
    /**
     * Get all available roles ordered by hierarchy
     * 
     * @return list of all roles ordered by hierarchy level (highest first)
     */
    @Transactional(readOnly = true)
    public List<Role> getAllRoles() {
        return roleRepository.findAllOrderedByHierarchy();
    }
    
    /**
     * Get roles available for a specific organization
     * 
     * @param organization the organization name
     * @return list of roles available for the organization
     */
    @Transactional(readOnly = true)
    public List<Role> getRolesForOrganization(String organization) {
        if (organization == null) {
            // For unknown organizations, return only ANALYST and GUEST roles
            return roleRepository.findRolesWithMaximumLevel(2); // ANALYST level and below
        }
        
        List<String> allowedRoleNames = ORGANIZATION_ALLOWED_ROLES.get(organization.toUpperCase());
        if (allowedRoleNames == null) {
            // For unknown organizations, return only ANALYST and GUEST roles
            return roleRepository.findRolesWithMaximumLevel(2); // ANALYST level and below
        }
        
        return allowedRoleNames.stream()
            .map(roleName -> roleRepository.findByName(roleName))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .sorted((r1, r2) -> Integer.compare(r2.getHierarchyLevel(), r1.getHierarchyLevel()))
            .toList();
    }
    
    /**
     * Get all permissions for a specific role
     * 
     * @param roleName the role name
     * @return list of permissions for the role
     */
    @Transactional(readOnly = true)
    public List<String> getPermissionsForRole(String roleName) {
        Optional<Role> roleOpt = roleRepository.findByName(roleName);
        if (roleOpt.isEmpty()) {
            return Collections.emptyList();
        }
        
        return roleOpt.get().getPermissions() != null ? 
            roleOpt.get().getPermissions() : Collections.emptyList();
    }
    
    /**
     * Check if a role has a specific permission
     * 
     * @param roleName the role name
     * @param permission the permission to check
     * @return true if the role has the permission, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean roleHasPermission(String roleName, Permission permission) {
        Optional<Role> roleOpt = roleRepository.findByName(roleName);
        if (roleOpt.isEmpty()) {
            return false;
        }
        
        return roleOpt.get().hasPermission(permission);
    }
    
    /**
     * Get user's current permissions
     * 
     * @param userId the user's ID
     * @return list of permissions for the user
     */
    @Transactional(readOnly = true)
    public List<String> getUserPermissions(UUID userId) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return Collections.emptyList();
        }
        
        User user = userOpt.get();
        return user.getRole().getPermissions() != null ? 
            user.getRole().getPermissions() : Collections.emptyList();
    }
    
    /**
     * Check if a user can assign a specific role (hierarchy-based check)
     * 
     * @param assignerUserId the ID of the user trying to assign the role
     * @param targetRoleName the role to be assigned
     * @return true if the assigner can assign the role, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean canAssignRole(UUID assignerUserId, String targetRoleName) {
        if (assignerUserId == null || targetRoleName == null) {
            return false;
        }
        
        try {
            Optional<User> assignerOpt = userRepository.findById(assignerUserId);
            if (assignerOpt.isEmpty()) {
                return false;
            }
            
            Optional<Role> targetRoleOpt = roleRepository.findByName(targetRoleName);
            if (targetRoleOpt.isEmpty()) {
                return false;
            }
            
            User assigner = assignerOpt.get();
            Role targetRole = targetRoleOpt.get();
            
            // Check if assigner has USER_MANAGEMENT permission
            if (!assigner.hasPermission(Permission.USER_MANAGEMENT)) {
                return false;
            }
            
            // Check hierarchy: can only assign roles with lower or equal hierarchy level
            return assigner.getRole().getHierarchyLevel() >= targetRole.getHierarchyLevel();
            
        } catch (Exception e) {
            logger.error("Error checking role assignment permission", e);
            return false;
        }
    }
}