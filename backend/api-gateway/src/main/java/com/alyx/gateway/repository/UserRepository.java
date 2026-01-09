package com.alyx.gateway.repository;

import com.alyx.gateway.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for User entity operations
 * 
 * Provides database access methods for user management, authentication,
 * and security operations with optimized queries.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    
    /**
     * Find user by email address (case-insensitive)
     * Used for authentication and registration validation
     */
    @Query("SELECT u FROM User u WHERE LOWER(u.email) = LOWER(:email)")
    Optional<User> findByEmail(@Param("email") String email);
    
    /**
     * Find user by email address with role information eagerly loaded
     * Optimized for authentication where role permissions are needed
     */
    @Query("SELECT u FROM User u JOIN FETCH u.role WHERE LOWER(u.email) = LOWER(:email)")
    Optional<User> findByEmailWithRole(@Param("email") String email);
    
    /**
     * Check if email exists (case-insensitive)
     * Used for registration validation without loading full user entity
     */
    @Query("SELECT COUNT(u) > 0 FROM User u WHERE LOWER(u.email) = LOWER(:email)")
    boolean existsByEmail(@Param("email") String email);
    
    /**
     * Find all active users
     * Used for user management and reporting
     */
    @Query("SELECT u FROM User u WHERE u.isActive = true ORDER BY u.createdAt DESC")
    List<User> findAllActiveUsers();
    
    /**
     * Find users by organization
     * Used for organizational user management
     */
    @Query("SELECT u FROM User u WHERE u.organization = :organization AND u.isActive = true ORDER BY u.lastName, u.firstName")
    List<User> findByOrganization(@Param("organization") String organization);
    
    /**
     * Find users by role name
     * Used for role-based user management
     */
    @Query("SELECT u FROM User u JOIN u.role r WHERE r.name = :roleName AND u.isActive = true ORDER BY u.lastName, u.firstName")
    List<User> findByRoleName(@Param("roleName") String roleName);
    
    /**
     * Find users with failed login attempts above threshold
     * Used for security monitoring and account lockout management
     */
    @Query("SELECT u FROM User u WHERE u.failedLoginAttempts >= :threshold AND u.isActive = true")
    List<User> findUsersWithFailedAttempts(@Param("threshold") int threshold);
    
    /**
     * Find currently locked accounts
     * Used for security monitoring and account management
     */
    @Query("SELECT u FROM User u WHERE u.lockedUntil IS NOT NULL AND u.lockedUntil > :currentTime")
    List<User> findLockedAccounts(@Param("currentTime") LocalDateTime currentTime);
    
    /**
     * Find users created within date range
     * Used for reporting and analytics
     */
    @Query("SELECT u FROM User u WHERE u.createdAt BETWEEN :startDate AND :endDate ORDER BY u.createdAt DESC")
    List<User> findUsersCreatedBetween(@Param("startDate") LocalDateTime startDate, 
                                      @Param("endDate") LocalDateTime endDate);
    
    /**
     * Find users who haven't logged in recently
     * Used for inactive user identification
     */
    @Query("SELECT u FROM User u WHERE u.lastLoginAt IS NULL OR u.lastLoginAt < :cutoffDate")
    List<User> findInactiveUsers(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    /**
     * Update user's last login timestamp
     * Optimized update query to avoid loading full entity
     */
    @Modifying
    @Query("UPDATE User u SET u.lastLoginAt = :loginTime WHERE u.id = :userId")
    void updateLastLogin(@Param("userId") UUID userId, @Param("loginTime") LocalDateTime loginTime);
    
    /**
     * Increment failed login attempts
     * Optimized update query for security tracking
     */
    @Modifying
    @Query("UPDATE User u SET u.failedLoginAttempts = u.failedLoginAttempts + 1 WHERE u.id = :userId")
    void incrementFailedLoginAttempts(@Param("userId") UUID userId);
    
    /**
     * Reset failed login attempts and unlock account
     * Used after successful login or manual unlock
     */
    @Modifying
    @Query("UPDATE User u SET u.failedLoginAttempts = 0, u.lockedUntil = NULL WHERE u.id = :userId")
    void resetFailedLoginAttempts(@Param("userId") UUID userId);
    
    /**
     * Lock user account until specified time
     * Used for security lockout after failed attempts
     */
    @Modifying
    @Query("UPDATE User u SET u.lockedUntil = :lockUntil WHERE u.id = :userId")
    void lockAccount(@Param("userId") UUID userId, @Param("lockUntil") LocalDateTime lockUntil);
    
    /**
     * Deactivate user account
     * Soft delete operation for user management
     */
    @Modifying
    @Query("UPDATE User u SET u.isActive = false WHERE u.id = :userId")
    void deactivateUser(@Param("userId") UUID userId);
    
    /**
     * Activate user account
     * Used for account reactivation
     */
    @Modifying
    @Query("UPDATE User u SET u.isActive = true WHERE u.id = :userId")
    void activateUser(@Param("userId") UUID userId);
    
    /**
     * Update user role
     * Used for role management operations
     */
    @Modifying
    @Query("UPDATE User u SET u.role.id = :roleId WHERE u.id = :userId")
    void updateUserRole(@Param("userId") UUID userId, @Param("roleId") UUID roleId);
    
    /**
     * Count users by role
     * Used for analytics and reporting
     */
    @Query("SELECT COUNT(u) FROM User u JOIN u.role r WHERE r.name = :roleName AND u.isActive = true")
    long countByRoleName(@Param("roleName") String roleName);
    
    /**
     * Count active users
     * Used for system metrics
     */
    @Query("SELECT COUNT(u) FROM User u WHERE u.isActive = true")
    long countActiveUsers();
}