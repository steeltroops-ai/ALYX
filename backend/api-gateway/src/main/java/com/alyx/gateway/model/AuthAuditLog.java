package com.alyx.gateway.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import io.hypersistence.utils.hibernate.type.json.JsonType;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Audit log entity for security event tracking
 * 
 * Records all authentication-related events for security monitoring,
 * compliance, and forensic analysis.
 */
@Entity
@Table(name = "auth_audit_log")
public class AuthAuditLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;
    
    @Column(name = "event_type", nullable = false, length = 50)
    @NotBlank(message = "Event type is required")
    private String eventType;
    
    @Type(JsonType.class)
    @Column(name = "event_details", columnDefinition = "jsonb")
    private Map<String, Object> eventDetails;
    
    @Column(name = "ip_address")
    private InetAddress ipAddress;
    
    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;
    
    @Column(nullable = false)
    @NotNull(message = "Success status is required")
    private Boolean success;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    // Constructors
    public AuthAuditLog() {}
    
    public AuthAuditLog(User user, String eventType, Map<String, Object> eventDetails, 
                       InetAddress ipAddress, String userAgent, Boolean success) {
        this.user = user;
        this.eventType = eventType;
        this.eventDetails = eventDetails;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.success = success;
    }
    
    // Static factory methods for common events
    public static AuthAuditLog loginSuccess(User user, InetAddress ipAddress, String userAgent) {
        return new AuthAuditLog(user, "LOGIN_SUCCESS", null, ipAddress, userAgent, true);
    }
    
    public static AuthAuditLog loginFailure(String email, InetAddress ipAddress, String userAgent, String reason) {
        Map<String, Object> details = Map.of(
            "email", email,
            "failure_reason", reason
        );
        return new AuthAuditLog(null, "LOGIN_FAILURE", details, ipAddress, userAgent, false);
    }
    
    public static AuthAuditLog registrationSuccess(User user, InetAddress ipAddress, String userAgent) {
        return new AuthAuditLog(user, "REGISTRATION_SUCCESS", null, ipAddress, userAgent, true);
    }
    
    public static AuthAuditLog registrationFailure(String email, InetAddress ipAddress, String userAgent, String reason) {
        Map<String, Object> details = Map.of(
            "email", email,
            "failure_reason", reason
        );
        return new AuthAuditLog(null, "REGISTRATION_FAILURE", details, ipAddress, userAgent, false);
    }
    
    public static AuthAuditLog logout(User user, InetAddress ipAddress, String userAgent) {
        return new AuthAuditLog(user, "LOGOUT", null, ipAddress, userAgent, true);
    }
    
    public static AuthAuditLog accountLocked(User user, InetAddress ipAddress, String userAgent) {
        Map<String, Object> details = Map.of(
            "failed_attempts", user.getFailedLoginAttempts(),
            "locked_until", user.getLockedUntil()
        );
        return new AuthAuditLog(user, "ACCOUNT_LOCKED", details, ipAddress, userAgent, false);
    }
    
    public static AuthAuditLog suspiciousActivity(User user, InetAddress ipAddress, String userAgent, String activity) {
        Map<String, Object> details = Map.of(
            "activity_type", activity
        );
        return new AuthAuditLog(user, "SUSPICIOUS_ACTIVITY", details, ipAddress, userAgent, false);
    }
    
    // Getters and Setters
    public UUID getId() {
        return id;
    }
    
    public void setId(UUID id) {
        this.id = id;
    }
    
    public User getUser() {
        return user;
    }
    
    public void setUser(User user) {
        this.user = user;
    }
    
    public String getEventType() {
        return eventType;
    }
    
    public void setEventType(String eventType) {
        this.eventType = eventType;
    }
    
    public Map<String, Object> getEventDetails() {
        return eventDetails;
    }
    
    public void setEventDetails(Map<String, Object> eventDetails) {
        this.eventDetails = eventDetails;
    }
    
    public InetAddress getIpAddress() {
        return ipAddress;
    }
    
    public void setIpAddress(InetAddress ipAddress) {
        this.ipAddress = ipAddress;
    }
    
    public String getUserAgent() {
        return userAgent;
    }
    
    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }
    
    public Boolean getSuccess() {
        return success;
    }
    
    public void setSuccess(Boolean success) {
        this.success = success;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AuthAuditLog)) return false;
        AuthAuditLog that = (AuthAuditLog) o;
        return id != null && id.equals(that.id);
    }
    
    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
    
    @Override
    public String toString() {
        return "AuthAuditLog{" +
                "id=" + id +
                ", eventType='" + eventType + '\'' +
                ", success=" + success +
                ", createdAt=" + createdAt +
                '}';
    }
}