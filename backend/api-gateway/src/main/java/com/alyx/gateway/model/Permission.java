package com.alyx.gateway.model;

import java.util.List;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * System permissions for fine-grained access control
 * 
 * Defines specific permissions that can be granted to users
 * based on their roles and organizational requirements.
 */
public enum Permission {
    // Data access permissions
    READ_DATA("Read collision event data"),
    READ_PUBLIC_DATA("Read public datasets only"),
    WRITE_DATA("Write/modify collision event data"),
    DELETE_DATA("Delete collision event data"),
    
    // Analysis permissions
    SUBMIT_JOBS("Submit analysis jobs"),
    CANCEL_JOBS("Cancel analysis jobs"),
    VIEW_RESULTS("View analysis results"),
    MODIFY_JOBS("Modify running jobs"),
    
    // Visualization permissions
    BASIC_VISUALIZATION("Basic 3D visualization access"),
    ADVANCED_VISUALIZATION("Advanced visualization features"),
    EXPORT_VISUALIZATIONS("Export visualization data"),
    
    // Collaboration permissions
    CREATE_SESSIONS("Create collaborative sessions"),
    JOIN_SESSIONS("Join collaborative sessions"),
    MANAGE_SESSIONS("Manage collaborative sessions"),
    
    // Notebook permissions
    CREATE_NOTEBOOKS("Create analysis notebooks"),
    EXECUTE_NOTEBOOKS("Execute notebook code"),
    SHARE_NOTEBOOKS("Share notebooks with others"),
    
    // System permissions
    USER_MANAGEMENT("Manage user accounts and roles"),
    SYSTEM_CONFIG("Configure system settings"),
    VIEW_METRICS("View system metrics and monitoring"),
    MANAGE_RESOURCES("Manage GRID resources");

    private final String description;

    Permission(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
    
    /**
     * Get all permissions for a specific user role
     */
    public static List<String> getPermissionsForRole(UserRole userRole) {
        return Arrays.stream(Permission.values())
                .filter(userRole::hasPermission)
                .map(Permission::name)
                .collect(Collectors.toList());
    }
}