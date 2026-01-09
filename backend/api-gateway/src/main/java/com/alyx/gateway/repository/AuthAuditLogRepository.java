package com.alyx.gateway.repository;

import com.alyx.gateway.model.AuthAuditLog;
import com.alyx.gateway.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository interface for AuthAuditLog entity operations
 * 
 * Provides database access methods for security event logging, audit trails,
 * and forensic analysis with optimized queries for large datasets.
 */
@Repository
public interface AuthAuditLogRepository extends JpaRepository<AuthAuditLog, UUID> {
    
    /**
     * Find audit logs for a specific user
     * Used for user-specific security monitoring
     */
    @Query("SELECT a FROM AuthAuditLog a WHERE a.user = :user ORDER BY a.createdAt DESC")
    Page<AuthAuditLog> findByUser(@Param("user") User user, Pageable pageable);
    
    /**
     * Find audit logs by event type
     * Used for specific event analysis (e.g., all login failures)
     */
    @Query("SELECT a FROM AuthAuditLog a WHERE a.eventType = :eventType ORDER BY a.createdAt DESC")
    Page<AuthAuditLog> findByEventType(@Param("eventType") String eventType, Pageable pageable);
    
    /**
     * Find audit logs by success status
     * Used for security monitoring (e.g., all failed events)
     */
    @Query("SELECT a FROM AuthAuditLog a WHERE a.success = :success ORDER BY a.createdAt DESC")
    Page<AuthAuditLog> findBySuccess(@Param("success") boolean success, Pageable pageable);
    
    /**
     * Find audit logs within date range
     * Used for time-based security analysis
     */
    @Query("SELECT a FROM AuthAuditLog a WHERE a.createdAt BETWEEN :startDate AND :endDate ORDER BY a.createdAt DESC")
    Page<AuthAuditLog> findByDateRange(@Param("startDate") LocalDateTime startDate, 
                                      @Param("endDate") LocalDateTime endDate, 
                                      Pageable pageable);
    
    /**
     * Find audit logs by IP address
     * Used for IP-based security analysis and threat detection
     */
    @Query("SELECT a FROM AuthAuditLog a WHERE a.ipAddress = :ipAddress ORDER BY a.createdAt DESC")
    Page<AuthAuditLog> findByIpAddress(@Param("ipAddress") InetAddress ipAddress, Pageable pageable);
    
    /**
     * Find recent failed login attempts for a user
     * Used for account lockout and security monitoring
     */
    @Query("SELECT a FROM AuthAuditLog a WHERE a.user = :user AND a.eventType = 'LOGIN_FAILURE' " +
           "AND a.createdAt > :since ORDER BY a.createdAt DESC")
    List<AuthAuditLog> findRecentFailedLogins(@Param("user") User user, @Param("since") LocalDateTime since);
    
    /**
     * Find failed login attempts from specific IP address
     * Used for IP-based threat detection
     */
    @Query("SELECT a FROM AuthAuditLog a WHERE a.ipAddress = :ipAddress AND a.eventType = 'LOGIN_FAILURE' " +
           "AND a.createdAt > :since ORDER BY a.createdAt DESC")
    List<AuthAuditLog> findFailedLoginsFromIp(@Param("ipAddress") InetAddress ipAddress, 
                                             @Param("since") LocalDateTime since);
    
    /**
     * Find suspicious activity events
     * Used for security monitoring and threat detection
     */
    @Query("SELECT a FROM AuthAuditLog a WHERE a.eventType = 'SUSPICIOUS_ACTIVITY' " +
           "AND a.createdAt > :since ORDER BY a.createdAt DESC")
    List<AuthAuditLog> findSuspiciousActivity(@Param("since") LocalDateTime since);
    
    /**
     * Count events by type within date range
     * Used for security metrics and reporting
     */
    @Query("SELECT COUNT(a) FROM AuthAuditLog a WHERE a.eventType = :eventType " +
           "AND a.createdAt BETWEEN :startDate AND :endDate")
    long countByEventTypeAndDateRange(@Param("eventType") String eventType, 
                                     @Param("startDate") LocalDateTime startDate, 
                                     @Param("endDate") LocalDateTime endDate);
    
    /**
     * Count failed events by user within date range
     * Used for user-specific security metrics
     */
    @Query("SELECT COUNT(a) FROM AuthAuditLog a WHERE a.user = :user AND a.success = false " +
           "AND a.createdAt BETWEEN :startDate AND :endDate")
    long countFailedEventsByUser(@Param("user") User user, 
                                @Param("startDate") LocalDateTime startDate, 
                                @Param("endDate") LocalDateTime endDate);
    
    /**
     * Find unique IP addresses with failed login attempts
     * Used for threat intelligence and IP blocking
     */
    @Query("SELECT DISTINCT a.ipAddress FROM AuthAuditLog a WHERE a.eventType = 'LOGIN_FAILURE' " +
           "AND a.createdAt > :since")
    List<InetAddress> findUniqueFailedLoginIps(@Param("since") LocalDateTime since);
    
    /**
     * Find most active IP addresses by event count
     * Used for traffic analysis and potential threat identification
     */
    @Query("SELECT a.ipAddress, COUNT(a) as eventCount FROM AuthAuditLog a " +
           "WHERE a.createdAt > :since GROUP BY a.ipAddress ORDER BY eventCount DESC")
    List<Object[]> findMostActiveIpAddresses(@Param("since") LocalDateTime since, Pageable pageable);
    
    /**
     * Find users with most failed login attempts
     * Used for security monitoring and user behavior analysis
     */
    @Query("SELECT a.user, COUNT(a) as failureCount FROM AuthAuditLog a " +
           "WHERE a.eventType = 'LOGIN_FAILURE' AND a.createdAt > :since " +
           "GROUP BY a.user ORDER BY failureCount DESC")
    List<Object[]> findUsersWithMostFailures(@Param("since") LocalDateTime since, Pageable pageable);
    
    /**
     * Delete old audit logs for retention management
     * Used for data retention policy enforcement
     */
    @Modifying
    @Query("DELETE FROM AuthAuditLog a WHERE a.createdAt < :cutoffDate")
    void deleteOldLogs(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    /**
     * Count total audit logs
     * Used for system metrics and storage monitoring
     */
    @Query("SELECT COUNT(a) FROM AuthAuditLog a")
    long countTotalLogs();
    
    /**
     * Find audit logs with specific event details
     * Used for detailed forensic analysis
     */
    @Query("SELECT a FROM AuthAuditLog a WHERE CAST(a.eventDetails AS string) LIKE %:searchTerm% " +
           "ORDER BY a.createdAt DESC")
    Page<AuthAuditLog> findByEventDetailsContaining(@Param("searchTerm") String searchTerm, Pageable pageable);
    
    /**
     * Find recent security events for dashboard
     * Used for security monitoring dashboard
     */
    @Query("SELECT a FROM AuthAuditLog a WHERE a.eventType IN :eventTypes " +
           "AND a.createdAt > :since ORDER BY a.createdAt DESC")
    List<AuthAuditLog> findRecentSecurityEvents(@Param("eventTypes") List<String> eventTypes, 
                                               @Param("since") LocalDateTime since, 
                                               Pageable pageable);
}