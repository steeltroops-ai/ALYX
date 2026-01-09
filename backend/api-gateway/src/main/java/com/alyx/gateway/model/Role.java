package com.alyx.gateway.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import io.hypersistence.utils.hibernate.type.json.JsonType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Role entity for role-based access control (RBAC)
 * 
 * Represents user roles with hierarchical permissions and database persistence.
 * Integrates with the existing UserRole enum for backward compatibility.
 */
@Entity
@Table(name = "roles")
public class Role {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(unique = true, nullable = false, length = 50)
    @NotBlank(message = "Role name is required")
    @Size(max = 50, message = "Role name must not exceed 50 characters")
    private String name;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private List<String> permissions;
    
    @Column(name = "hierarchy_level", nullable = false)
    @NotNull(message = "Hierarchy level is required")
    private Integer hierarchyLevel;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    // Constructors
    public Role() {}
    
    public Role(String name, String description, List<String> permissions, Integer hierarchyLevel) {
        this.name = name;
        this.description = description;
        this.permissions = permissions;
        this.hierarchyLevel = hierarchyLevel;
    }
    
    // Factory method to create Role from UserRole enum
    public static Role fromUserRole(UserRole userRole) {
        List<String> rolePermissions = Permission.getPermissionsForRole(userRole);
        return new Role(
            userRole.getRoleName(),
            userRole.getDescription(),
            rolePermissions,
            userRole.getHierarchyLevel()
        );
    }
    
    // Getters and Setters
    public UUID getId() {
        return id;
    }
    
    public void setId(UUID id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public List<String> getPermissions() {
        return permissions;
    }
    
    public void setPermissions(List<String> permissions) {
        this.permissions = permissions;
    }
    
    public Integer getHierarchyLevel() {
        return hierarchyLevel;
    }
    
    public void setHierarchyLevel(Integer hierarchyLevel) {
        this.hierarchyLevel = hierarchyLevel;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    // Business methods
    public boolean hasPermission(String permission) {
        return permissions != null && permissions.contains(permission);
    }
    
    public boolean hasPermission(Permission permission) {
        return hasPermission(permission.name());
    }
    
    public UserRole toUserRole() {
        return UserRole.fromString(this.name);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Role)) return false;
        Role role = (Role) o;
        return id != null && id.equals(role.id);
    }
    
    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
    
    @Override
    public String toString() {
        return "Role{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", hierarchyLevel=" + hierarchyLevel +
                '}';
    }
}