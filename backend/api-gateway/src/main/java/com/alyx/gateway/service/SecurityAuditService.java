package com.alyx.gateway.service;

import com.alyx.gateway.model.AuthAuditLog;
import com.alyx.gateway.model.User;
import com.alyx.gateway.repository.AuthAuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Security audit service for comprehensive logging and monitoring of security events
 * 
 * Provides database-backed audit logging, threat detection, retention management,
 * and audit trail functionality for compliance and security monitoring.
 * 
 * Requirements 5.5: Log security events for monitoring when suspicious activity is detected
 * Requirements 7.5: Log all authentication events with appropriate detail levels for security auditing
 */
@Service
public class SecurityAuditService {

    private static final Logger securityLogger = LoggerFactory.getLogger("SECURITY_AUDIT");
    private static final Logger alertLogger = LoggerFactory.getLogger("SECURITY_ALERT");
    
    private final AuthAuditLogRepository auditLogRepository;
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    
    // Threat detection thresholds
    private static final int FAILED_LOGIN_THRESHOLD = 5;
    private static final int SUSPICIOUS_REQUEST_THRESHOLD = 50;
    private static final Duration THREAT_DETECTION_WINDOW = Duration.ofMinutes(15);
    
    // Audit retention configuration
    @Value("${security.audit.retention-days:90}")
    private int auditRetentionDays;
    
    @Value("${security.audit.enabled:true}")
    private boolean auditEnabled;

    @Autowired
    public SecurityAuditService(AuthAuditLogRepository auditLogRepository, 
                               ReactiveRedisTemplate<String, String> redisTemplate) {
        this.auditLogRepository = auditLogRepository;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Log authentication success event to database and structured logs
     * Requirements 7.5: Log all authentication events with appropriate detail levels
     */
    @Async
    @Transactional
    public void logAuthenticationSuccess(User user, String ipAddress, String userAgent) {
        if (!auditEnabled) return;
        
        try {
            // Create database audit log entry
            AuthAuditLog auditLog = AuthAuditLog.loginSuccess(user, parseIpAddress(ipAddress), userAgent);
            auditLogRepository.save(auditLog);
            
            // Structured logging
            logSecurityEventWithContext("LOGIN_SUCCESS", "/api/auth/login", user.getId().toString(), 
                "User successfully authenticated", createAuthContext(user, ipAddress, userAgent, true));
            
            // Reset failed login attempts on successful login
            resetFailedLoginAttempts(user);
            
        } catch (Exception e) {
            securityLogger.error("Failed to log authentication success for user {}: {}", 
                user.getEmail(), e.getMessage());
        }
    }

    /**
     * Log authentication failure event to database and structured logs
     * Requirements 7.5: Log all authentication events with appropriate detail levels
     */
    @Async
    @Transactional
    public void logAuthenticationFailure(String email, String ipAddress, String userAgent, String reason) {
        if (!auditEnabled) return;
        
        try {
            // Create database audit log entry
            AuthAuditLog auditLog = AuthAuditLog.loginFailure(email, parseIpAddress(ipAddress), userAgent, reason);
            auditLogRepository.save(auditLog);
            
            // Structured logging
            Map<String, String> context = createFailureContext(email, ipAddress, userAgent, reason);
            logSecurityEventWithContext("LOGIN_FAILURE", "/api/auth/login", email, 
                "Authentication failed: " + reason, context);
            
            // Track failed login attempts for threat detection
            trackFailedLoginAttempt(email, ipAddress).subscribe();
            
        } catch (Exception e) {
            securityLogger.error("Failed to log authentication failure for email {}: {}", 
                email, e.getMessage());
        }
    }

    /**
     * Log user registration success event
     * Requirements 7.5: Log all authentication events with appropriate detail levels
     */
    @Async
    @Transactional
    public void logRegistrationSuccess(User user, String ipAddress, String userAgent) {
        if (!auditEnabled) return;
        
        try {
            AuthAuditLog auditLog = AuthAuditLog.registrationSuccess(user, parseIpAddress(ipAddress), userAgent);
            auditLogRepository.save(auditLog);
            
            logSecurityEventWithContext("REGISTRATION_SUCCESS", "/api/auth/register", user.getId().toString(),
                "User successfully registered", createAuthContext(user, ipAddress, userAgent, true));
            
        } catch (Exception e) {
            securityLogger.error("Failed to log registration success for user {}: {}", 
                user.getEmail(), e.getMessage());
        }
    }

    /**
     * Log user registration failure event
     * Requirements 7.5: Log all authentication events with appropriate detail levels
     */
    @Async
    @Transactional
    public void logRegistrationFailure(String email, String ipAddress, String userAgent, String reason) {
        if (!auditEnabled) return;
        
        try {
            AuthAuditLog auditLog = AuthAuditLog.registrationFailure(email, parseIpAddress(ipAddress), userAgent, reason);
            auditLogRepository.save(auditLog);
            
            Map<String, String> context = createFailureContext(email, ipAddress, userAgent, reason);
            logSecurityEventWithContext("REGISTRATION_FAILURE", "/api/auth/register", email,
                "Registration failed: " + reason, context);
            
        } catch (Exception e) {
            securityLogger.error("Failed to log registration failure for email {}: {}", 
                email, e.getMessage());
        }
    }

    /**
     * Log user logout event
     * Requirements 7.5: Log all authentication events with appropriate detail levels
     */
    @Async
    @Transactional
    public void logLogout(User user, String ipAddress, String userAgent) {
        if (!auditEnabled) return;
        
        try {
            AuthAuditLog auditLog = AuthAuditLog.logout(user, parseIpAddress(ipAddress), userAgent);
            auditLogRepository.save(auditLog);
            
            logSecurityEventWithContext("LOGOUT", "/api/auth/logout", user.getId().toString(),
                "User logged out", createAuthContext(user, ipAddress, userAgent, true));
            
        } catch (Exception e) {
            securityLogger.error("Failed to log logout for user {}: {}", 
                user.getEmail(), e.getMessage());
        }
    }

    /**
     * Log account lockout event
     * Requirements 5.5: Log security events for monitoring when suspicious activity is detected
     */
    @Async
    @Transactional
    public void logAccountLocked(User user, String ipAddress, String userAgent) {
        if (!auditEnabled) return;
        
        try {
            AuthAuditLog auditLog = AuthAuditLog.accountLocked(user, parseIpAddress(ipAddress), userAgent);
            auditLogRepository.save(auditLog);
            
            Map<String, String> context = createAuthContext(user, ipAddress, userAgent, false);
            context.put("severity", "HIGH");
            context.put("failedAttempts", String.valueOf(user.getFailedLoginAttempts()));
            context.put("lockedUntil", user.getLockedUntil().toString());
            
            logSecurityEventWithContext("ACCOUNT_LOCKED", "/api/auth/login", user.getId().toString(),
                "Account locked due to failed login attempts", context);
            
            // Generate security alert
            alertLogger.warn("SECURITY ALERT: Account locked for user {} after {} failed attempts from IP {}", 
                user.getEmail(), user.getFailedLoginAttempts(), ipAddress);
            
        } catch (Exception e) {
            securityLogger.error("Failed to log account lockout for user {}: {}", 
                user.getEmail(), e.getMessage());
        }
    }

    /**
     * Log suspicious activity event
     * Requirements 5.5: Log security events for monitoring when suspicious activity is detected
     */
    @Async
    @Transactional
    public void logSuspiciousActivity(User user, String ipAddress, String userAgent, String activity) {
        if (!auditEnabled) return;
        
        try {
            AuthAuditLog auditLog = AuthAuditLog.suspiciousActivity(user, parseIpAddress(ipAddress), userAgent, activity);
            auditLogRepository.save(auditLog);
            
            Map<String, String> context = createAuthContext(user, ipAddress, userAgent, false);
            context.put("severity", "HIGH");
            context.put("activityType", activity);
            
            logSecurityEventWithContext("SUSPICIOUS_ACTIVITY", "various", user.getId().toString(),
                "Suspicious activity detected: " + activity, context);
            
            // Generate security alert
            alertLogger.warn("SECURITY ALERT: Suspicious activity detected for user {} from IP {}: {}", 
                user.getEmail(), ipAddress, activity);
            
        } catch (Exception e) {
            securityLogger.error("Failed to log suspicious activity for user {}: {}", 
                user.getEmail(), e.getMessage());
        }
    }

    /**
     * Log token validation failure
     * Requirements 5.5: Log security events for monitoring when suspicious activity is detected
     */
    @Async
    @Transactional
    public void logTokenValidationFailure(String token, String ipAddress, String userAgent, String reason) {
        if (!auditEnabled) return;
        
        try {
            Map<String, Object> eventDetails = Map.of(
                "tokenPrefix", token != null ? token.substring(0, Math.min(10, token.length())) + "..." : "null",
                "reason", reason,
                "ipAddress", ipAddress,
                "userAgent", userAgent
            );
            
            AuthAuditLog auditLog = new AuthAuditLog(null, "TOKEN_VALIDATION_FAILURE", eventDetails, 
                parseIpAddress(ipAddress), userAgent, false);
            auditLogRepository.save(auditLog);
            
            Map<String, String> context = Map.of(
                "ipAddress", ipAddress,
                "userAgent", userAgent,
                "reason", reason,
                "severity", "MEDIUM"
            );
            
            logSecurityEventWithContext("TOKEN_VALIDATION_FAILURE", "/api/**", "unknown",
                "Token validation failed: " + reason, context);
            
        } catch (Exception e) {
            securityLogger.error("Failed to log token validation failure: {}", e.getMessage());
        }
    }

    /**
     * Get audit logs for a specific user with pagination
     */
    public Page<AuthAuditLog> getUserAuditLogs(User user, Pageable pageable) {
        return auditLogRepository.findByUser(user, pageable);
    }

    /**
     * Get audit logs by event type with pagination
     */
    public Page<AuthAuditLog> getAuditLogsByEventType(String eventType, Pageable pageable) {
        return auditLogRepository.findByEventType(eventType, pageable);
    }

    /**
     * Get failed audit events with pagination
     */
    public Page<AuthAuditLog> getFailedEvents(Pageable pageable) {
        return auditLogRepository.findBySuccess(false, pageable);
    }

    /**
     * Get audit logs within date range
     */
    public Page<AuthAuditLog> getAuditLogsByDateRange(LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        return auditLogRepository.findByDateRange(startDate, endDate, pageable);
    }

    /**
     * Get recent failed login attempts for a user
     */
    public List<AuthAuditLog> getRecentFailedLogins(User user, LocalDateTime since) {
        return auditLogRepository.findRecentFailedLogins(user, since);
    }

    /**
     * Get failed login attempts from specific IP address
     */
    public List<AuthAuditLog> getFailedLoginsFromIp(InetAddress ipAddress, LocalDateTime since) {
        return auditLogRepository.findFailedLoginsFromIp(ipAddress, since);
    }

    /**
     * Get recent suspicious activity events
     */
    public List<AuthAuditLog> getSuspiciousActivity(LocalDateTime since) {
        return auditLogRepository.findSuspiciousActivity(since);
    }

    /**
     * Generate comprehensive security report
     */
    public Map<String, Object> generateSecurityReport(LocalDateTime startDate, LocalDateTime endDate) {
        Map<String, Object> report = new HashMap<>();
        
        // Report period
        report.put("reportPeriod", Map.of(
            "start", startDate.toString(),
            "end", endDate.toString()
        ));
        
        // Event counts by type
        Map<String, Long> eventCounts = new HashMap<>();
        eventCounts.put("loginSuccess", auditLogRepository.countByEventTypeAndDateRange("LOGIN_SUCCESS", startDate, endDate));
        eventCounts.put("loginFailure", auditLogRepository.countByEventTypeAndDateRange("LOGIN_FAILURE", startDate, endDate));
        eventCounts.put("registrationSuccess", auditLogRepository.countByEventTypeAndDateRange("REGISTRATION_SUCCESS", startDate, endDate));
        eventCounts.put("registrationFailure", auditLogRepository.countByEventTypeAndDateRange("REGISTRATION_FAILURE", startDate, endDate));
        eventCounts.put("accountLocked", auditLogRepository.countByEventTypeAndDateRange("ACCOUNT_LOCKED", startDate, endDate));
        eventCounts.put("suspiciousActivity", auditLogRepository.countByEventTypeAndDateRange("SUSPICIOUS_ACTIVITY", startDate, endDate));
        
        report.put("eventCounts", eventCounts);
        
        // Security metrics
        long totalEvents = eventCounts.values().stream().mapToLong(Long::longValue).sum();
        long failedEvents = eventCounts.get("loginFailure") + eventCounts.get("registrationFailure") + 
                           eventCounts.get("accountLocked") + eventCounts.get("suspiciousActivity");
        
        report.put("summary", Map.of(
            "totalEvents", totalEvents,
            "failedEvents", failedEvents,
            "successRate", totalEvents > 0 ? (double)(totalEvents - failedEvents) / totalEvents * 100 : 0.0
        ));
        
        // Top threat indicators
        Pageable topTen = PageRequest.of(0, 10);
        report.put("threatIndicators", Map.of(
            "mostActiveIps", auditLogRepository.findMostActiveIpAddresses(startDate, topTen),
            "usersWithMostFailures", auditLogRepository.findUsersWithMostFailures(startDate, topTen),
            "uniqueFailedLoginIps", auditLogRepository.findUniqueFailedLoginIps(startDate).size()
        ));
        
        return report;
    }

    /**
     * Scheduled task to clean up old audit logs based on retention policy
     * Requirements 7.5: Maintain audit log retention policies
     */
    @Scheduled(cron = "0 0 2 * * ?") // Run daily at 2 AM
    @Transactional
    public void cleanupOldAuditLogs() {
        if (!auditEnabled) return;
        
        try {
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(auditRetentionDays);
            long deletedCount = auditLogRepository.countTotalLogs();
            
            auditLogRepository.deleteOldLogs(cutoffDate);
            
            long remainingCount = auditLogRepository.countTotalLogs();
            long actualDeleted = deletedCount - remainingCount;
            
            securityLogger.info("Audit log cleanup completed: {} logs deleted, {} logs retained (retention: {} days)", 
                actualDeleted, remainingCount, auditRetentionDays);
            
        } catch (Exception e) {
            securityLogger.error("Failed to cleanup old audit logs: {}", e.getMessage());
        }
    }

    /**
     * Reset failed login attempts for a user
     */
    private void resetFailedLoginAttempts(User user) {
        try {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            // Note: User will be saved by the calling service
        } catch (Exception e) {
            securityLogger.error("Failed to reset failed login attempts for user {}: {}", 
                user.getEmail(), e.getMessage());
        }
    }

    /**
     * Create authentication context for structured logging
     */
    private Map<String, String> createAuthContext(User user, String ipAddress, String userAgent, boolean success) {
        Map<String, String> context = new HashMap<>();
        context.put("userId", user.getId().toString());
        context.put("email", user.getEmail());
        context.put("ipAddress", ipAddress);
        context.put("userAgent", userAgent);
        context.put("success", String.valueOf(success));
        context.put("role", user.getRole() != null ? user.getRole().getName() : "unknown");
        return context;
    }

    /**
     * Create failure context for structured logging
     */
    private Map<String, String> createFailureContext(String email, String ipAddress, String userAgent, String reason) {
        Map<String, String> context = new HashMap<>();
        context.put("email", email);
        context.put("ipAddress", ipAddress);
        context.put("userAgent", userAgent);
        context.put("reason", reason);
        context.put("success", "false");
        context.put("severity", "MEDIUM");
        return context;
    }

    /**
     * Parse IP address string to InetAddress
     */
    private InetAddress parseIpAddress(String ipAddress) {
        try {
            return InetAddress.getByName(ipAddress);
        } catch (UnknownHostException e) {
            securityLogger.warn("Failed to parse IP address {}: {}", ipAddress, e.getMessage());
            try {
                return InetAddress.getByName("0.0.0.0");
            } catch (UnknownHostException ex) {
                return null;
            }
        }
    }

    /**
     * Log security event with structured logging (legacy method for backward compatibility)
     */
    public void logSecurityEvent(String eventType, String resource, String userId, String details) {
        try {
            // Set MDC context for structured logging
            MDC.put("eventType", eventType);
            MDC.put("resource", resource);
            MDC.put("userId", userId != null ? userId : "anonymous");
            MDC.put("timestamp", Instant.now().toString());
            MDC.put("details", details);
            
            // Log the security event
            securityLogger.info("Security Event: {} | Resource: {} | User: {} | Details: {}", 
                eventType, resource, userId, details);
            
            // Store in Redis for threat detection
            storeSecurityEvent(eventType, userId, resource).subscribe();
            
            // Check for suspicious patterns
            checkThreatPatterns(eventType, userId).subscribe();
            
        } finally {
            MDC.clear();
        }
    }

    /**
     * Log privilege escalation attempt
     */
    public void logPrivilegeEscalation(String userId, String attemptedResource, String currentRole, String requiredRole) {
        Map<String, String> context = new HashMap<>();
        context.put("currentRole", currentRole);
        context.put("requiredRole", requiredRole);
        context.put("severity", "CRITICAL");
        
        String details = String.format("Privilege escalation attempt: %s -> %s", currentRole, requiredRole);
        logSecurityEventWithContext("PRIVILEGE_ESCALATION", attemptedResource, userId, details, context);
        
        // Immediate alert for privilege escalation
        alertLogger.error("CRITICAL: Privilege escalation attempt by user {} for resource {}", 
            userId, attemptedResource);
    }

    /**
     * Log data access event
     */
    public void logDataAccess(String userId, String dataType, String operation, boolean success) {
        String eventType = success ? "DATA_ACCESS_SUCCESS" : "DATA_ACCESS_FAILURE";
        String details = String.format("Operation: %s on %s", operation, dataType);
        
        Map<String, String> context = new HashMap<>();
        context.put("dataType", dataType);
        context.put("operation", operation);
        context.put("success", String.valueOf(success));
        
        logSecurityEventWithContext(eventType, "/api/data/" + dataType, userId, details, context);
    }

    /**
     * Log suspicious activity (legacy method)
     */
    public void logSuspiciousActivity(String userId, String ipAddress, String activity, String riskLevel) {
        Map<String, String> context = new HashMap<>();
        context.put("ipAddress", ipAddress);
        context.put("activity", activity);
        context.put("riskLevel", riskLevel);
        context.put("severity", riskLevel);
        
        logSecurityEventWithContext("SUSPICIOUS_ACTIVITY", "various", userId, activity, context);
        
        if ("HIGH".equals(riskLevel) || "CRITICAL".equals(riskLevel)) {
            alertLogger.warn("Suspicious activity detected: User {} from IP {} - {}", 
                userId, ipAddress, activity);
        }
    }

    /**
     * Log security event with additional context
     */
    private void logSecurityEventWithContext(String eventType, String resource, String userId, 
                                           String details, Map<String, String> context) {
        try {
            // Set base MDC context
            MDC.put("eventType", eventType);
            MDC.put("resource", resource);
            MDC.put("userId", userId != null ? userId : "anonymous");
            MDC.put("timestamp", Instant.now().toString());
            MDC.put("details", details);
            
            // Add additional context
            context.forEach(MDC::put);
            
            // Determine log level based on severity
            String severity = context.getOrDefault("severity", "INFO");
            switch (severity) {
                case "CRITICAL" -> securityLogger.error("CRITICAL Security Event: {} | Resource: {} | User: {} | Details: {}", 
                    eventType, resource, userId, details);
                case "HIGH" -> securityLogger.warn("HIGH Security Event: {} | Resource: {} | User: {} | Details: {}", 
                    eventType, resource, userId, details);
                default -> securityLogger.info("Security Event: {} | Resource: {} | User: {} | Details: {}", 
                    eventType, resource, userId, details);
            }
            
        } finally {
            MDC.clear();
        }
    }

    /**
     * Store security event in Redis for threat detection
     */
    private Mono<Void> storeSecurityEvent(String eventType, String userId, String resource) {
        String key = "security_events:" + eventType + ":" + (userId != null ? userId : "anonymous");
        long timestamp = Instant.now().getEpochSecond();
        
        return redisTemplate.opsForZSet()
            .add(key, resource + ":" + timestamp, timestamp)
            .then(redisTemplate.expire(key, THREAT_DETECTION_WINDOW))
            .then();
    }

    /**
     * Track failed login attempts for brute force detection
     */
    private Mono<Void> trackFailedLoginAttempt(String userId, String ipAddress) {
        String userKey = "failed_logins:user:" + userId;
        String ipKey = "failed_logins:ip:" + ipAddress;
        long timestamp = Instant.now().getEpochSecond();
        
        return Mono.when(
            redisTemplate.opsForZSet().add(userKey, String.valueOf(timestamp), timestamp)
                .then(redisTemplate.expire(userKey, THREAT_DETECTION_WINDOW)),
            redisTemplate.opsForZSet().add(ipKey, String.valueOf(timestamp), timestamp)
                .then(redisTemplate.expire(ipKey, THREAT_DETECTION_WINDOW))
        );
    }

    /**
     * Check for threat patterns and generate alerts
     */
    private Mono<Void> checkThreatPatterns(String eventType, String userId) {
        if ("AUTH_FAILURE".equals(eventType) && userId != null) {
            return checkBruteForceAttack(userId);
        }
        
        if ("ACCESS_DENIED".equals(eventType) || "PERMISSION_DENIED".equals(eventType)) {
            return checkSuspiciousRequestPattern(userId);
        }
        
        return Mono.empty();
    }

    /**
     * Check for brute force attack patterns
     */
    private Mono<Void> checkBruteForceAttack(String userId) {
        String key = "failed_logins:user:" + userId;
        long windowStart = Instant.now().minus(THREAT_DETECTION_WINDOW).getEpochSecond();
        
        return redisTemplate.opsForZSet()
            .count(key, Range.closed((double)windowStart, (double)Instant.now().getEpochSecond()))
            .doOnNext(count -> {
                if (count >= FAILED_LOGIN_THRESHOLD) {
                    alertLogger.error("SECURITY ALERT: Potential brute force attack detected for user: {} ({} failed attempts in {} minutes)", 
                        userId, count, THREAT_DETECTION_WINDOW.toMinutes());
                    
                    // Log critical security event
                    logSecurityEvent("BRUTE_FORCE_DETECTED", "/api/auth/login", userId, 
                        String.format("Brute force attack detected: %d failed attempts", count));
                }
            })
            .then();
    }

    /**
     * Check for suspicious request patterns
     */
    private Mono<Void> checkSuspiciousRequestPattern(String userId) {
        if (userId == null) return Mono.empty();
        
        String key = "security_events:ACCESS_DENIED:" + userId;
        long windowStart = Instant.now().minus(THREAT_DETECTION_WINDOW).getEpochSecond();
        
        return redisTemplate.opsForZSet()
            .count(key, Range.closed((double)windowStart, (double)Instant.now().getEpochSecond()))
            .doOnNext(count -> {
                if (count >= SUSPICIOUS_REQUEST_THRESHOLD) {
                    alertLogger.warn("SECURITY ALERT: Suspicious request pattern detected for user: {} ({} denied requests in {} minutes)", 
                        userId, count, THREAT_DETECTION_WINDOW.toMinutes());
                    
                    logSuspiciousActivity(userId, "unknown", 
                        String.format("High volume of denied requests: %d in %d minutes", count, THREAT_DETECTION_WINDOW.toMinutes()), 
                        "HIGH");
                }
            })
            .then();
    }

    /**
     * Generate security report for a specific time period (legacy method)
     */
    public Mono<Map<String, Object>> generateSecurityReport(Instant startTime, Instant endTime) {
        // Convert to LocalDateTime and use the comprehensive report method
        LocalDateTime start = LocalDateTime.ofInstant(startTime, java.time.ZoneOffset.UTC);
        LocalDateTime end = LocalDateTime.ofInstant(endTime, java.time.ZoneOffset.UTC);
        
        return Mono.just(generateSecurityReport(start, end));
    }
}