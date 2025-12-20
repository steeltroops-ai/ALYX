package com.alyx.gateway.model;

/**
 * User roles for role-based access control (RBAC)
 * 
 * Defines the hierarchy of user roles in the ALYX system
 * with specific permissions for different operations.
 */
public enum UserRole {
    ADMIN("ADMIN", "Full system access including user management and system configuration"),
    PHYSICIST("PHYSICIST", "Full analysis access including job submission and data visualization"),
    ANALYST("ANALYST", "Read-only access to analysis results and basic visualization"),
    GUEST("GUEST", "Limited read-only access to public datasets");

    private final String roleName;
    private final String description;

    UserRole(String roleName, String description) {
        this.roleName = roleName;
        this.description = description;
    }

    public String getRoleName() {
        return roleName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Check if this role has permission for a specific operation
     */
    public boolean hasPermission(Permission permission) {
        return switch (this) {
            case ADMIN -> true; // Admin has all permissions
            case PHYSICIST -> permission != Permission.USER_MANAGEMENT && 
                            permission != Permission.SYSTEM_CONFIG;
            case ANALYST -> permission == Permission.READ_DATA || 
                          permission == Permission.VIEW_RESULTS ||
                          permission == Permission.BASIC_VISUALIZATION;
            case GUEST -> permission == Permission.READ_PUBLIC_DATA;
        };
    }

    /**
     * Get role hierarchy level (higher number = more privileges)
     */
    public int getHierarchyLevel() {
        return switch (this) {
            case ADMIN -> 4;
            case PHYSICIST -> 3;
            case ANALYST -> 2;
            case GUEST -> 1;
        };
    }

    public static UserRole fromString(String role) {
        if (role == null) {
            return GUEST;
        }
        
        try {
            return UserRole.valueOf(role.toUpperCase());
        } catch (IllegalArgumentException e) {
            return GUEST;
        }
    }
}