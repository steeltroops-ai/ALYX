package com.alyx.gateway.repository;

import com.alyx.gateway.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for Role entity operations
 * 
 * Provides database access methods for role management, permission lookups,
 * and hierarchical role operations.
 */
@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {
    
    /**
     * Find role by name (case-insensitive)
     * Used for role assignment and validation
     */
    @Query("SELECT r FROM Role r WHERE LOWER(r.name) = LOWER(:name)")
    Optional<Role> findByName(@Param("name") String name);
    
    /**
     * Check if role name exists (case-insensitive)
     * Used for role creation validation
     */
    @Query("SELECT COUNT(r) > 0 FROM Role r WHERE LOWER(r.name) = LOWER(:name)")
    boolean existsByName(@Param("name") String name);
    
    /**
     * Find all roles ordered by hierarchy level (highest first)
     * Used for role management and display
     */
    @Query("SELECT r FROM Role r ORDER BY r.hierarchyLevel DESC, r.name ASC")
    List<Role> findAllOrderedByHierarchy();
    
    /**
     * Find roles with hierarchy level greater than or equal to specified level
     * Used for role-based access control and permission inheritance
     */
    @Query("SELECT r FROM Role r WHERE r.hierarchyLevel >= :minLevel ORDER BY r.hierarchyLevel DESC")
    List<Role> findRolesWithMinimumLevel(@Param("minLevel") int minLevel);
    
    /**
     * Find roles with hierarchy level less than or equal to specified level
     * Used for role assignment restrictions
     */
    @Query("SELECT r FROM Role r WHERE r.hierarchyLevel <= :maxLevel ORDER BY r.hierarchyLevel DESC")
    List<Role> findRolesWithMaximumLevel(@Param("maxLevel") int maxLevel);
    
    /**
     * Find roles that have a specific permission
     * Used for permission-based role queries
     */
    @Query("SELECT r FROM Role r WHERE :permission MEMBER OF r.permissions ORDER BY r.hierarchyLevel DESC")
    List<Role> findRolesWithPermission(@Param("permission") String permission);
    
    /**
     * Find roles that have any of the specified permissions
     * Used for complex permission-based queries
     */
    @Query("SELECT DISTINCT r FROM Role r WHERE EXISTS " +
           "(SELECT 1 FROM r.permissions p WHERE p IN :permissions) " +
           "ORDER BY r.hierarchyLevel DESC")
    List<Role> findRolesWithAnyPermission(@Param("permissions") List<String> permissions);
    
    /**
     * Find roles that have all of the specified permissions
     * Used for strict permission requirement queries
     */
    @Query("SELECT r FROM Role r WHERE " +
           "(SELECT COUNT(p) FROM r.permissions p WHERE p IN :permissions) = :permissionCount " +
           "ORDER BY r.hierarchyLevel DESC")
    List<Role> findRolesWithAllPermissions(@Param("permissions") List<String> permissions, 
                                          @Param("permissionCount") long permissionCount);
    
    /**
     * Find default role for new users
     * Assumes the role with the lowest hierarchy level is the default
     */
    @Query("SELECT r FROM Role r WHERE r.hierarchyLevel = (SELECT MIN(r2.hierarchyLevel) FROM Role r2)")
    Optional<Role> findDefaultRole();
    
    /**
     * Find role with highest hierarchy level (admin role)
     * Used for administrative operations
     */
    @Query("SELECT r FROM Role r WHERE r.hierarchyLevel = (SELECT MAX(r2.hierarchyLevel) FROM Role r2)")
    Optional<Role> findHighestRole();
    
    /**
     * Count roles by hierarchy level
     * Used for analytics and role distribution reporting
     */
    @Query("SELECT COUNT(r) FROM Role r WHERE r.hierarchyLevel = :level")
    long countByHierarchyLevel(@Param("level") int level);
    
    /**
     * Find roles created within a specific time range
     * Used for audit and reporting purposes
     */
    @Query("SELECT r FROM Role r WHERE r.createdAt BETWEEN :startDate AND :endDate ORDER BY r.createdAt DESC")
    List<Role> findRolesCreatedBetween(@Param("startDate") java.time.LocalDateTime startDate, 
                                      @Param("endDate") java.time.LocalDateTime endDate);
    
    /**
     * Get all unique permissions across all roles
     * Used for permission management and validation
     */
    @Query("SELECT DISTINCT p FROM Role r JOIN r.permissions p ORDER BY p")
    List<String> findAllUniquePermissions();
    
    /**
     * Find roles suitable for a specific organization level
     * This is a business logic method that can be customized based on organization requirements
     */
    @Query("SELECT r FROM Role r WHERE r.hierarchyLevel BETWEEN :minLevel AND :maxLevel ORDER BY r.hierarchyLevel DESC")
    List<Role> findRolesForOrganizationLevel(@Param("minLevel") int minLevel, @Param("maxLevel") int maxLevel);
}