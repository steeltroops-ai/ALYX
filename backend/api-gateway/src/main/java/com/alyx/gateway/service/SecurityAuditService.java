package com.alyx.gateway.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Security audit service for logging and monitoring security events
 * 
 * Provides comprehensive security event logging, threat detection,
 * and audit trail functionality for compliance and security monitoring.
 */
@Service
public class SecurityAuditService {

    private static final Logger securityLogger = LoggerFactory.getLogger("SECURITY_AUDIT");
    private static final Logger alertLogger = LoggerFactory.getLogger("SECURITY_ALERT");
    
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    
    // Threat detection thresholds
    private static final int FAILED_LOGIN_THRESHOLD = 5;
    private static final int SUSPICIOUS_REQUEST_THRESHOLD = 50;
    private static final Duration THREAT_DETECTION_WINDOW = Duration.ofMinutes(15);

    public SecurityAuditService(ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Log security event with structured logging
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
     * Log authentication failure with enhanced details
     */
    public void logAuthenticationFailure(String userId, String ipAddress, String userAgent, String reason) {
        Map<String, String> context = new HashMap<>();
        context.put("ipAddress", ipAddress);
        context.put("userAgent", userAgent);
        context.put("reason", reason);
        context.put("severity", "HIGH");
        
        logSecurityEventWithContext("AUTH_FAILURE", "/api/auth/login", userId, reason, context);
        
        // Track failed login attempts for threat detection
        trackFailedLoginAttempt(userId, ipAddress).subscribe();
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
     * Log suspicious activity
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
     * Generate security report for a specific time period
     */
    public Mono<Map<String, Object>> generateSecurityReport(Instant startTime, Instant endTime) {
        // This would typically query a persistent audit log database
        // For now, we'll return a basic structure
        Map<String, Object> report = new HashMap<>();
        report.put("reportPeriod", Map.of(
            "start", startTime.toString(),
            "end", endTime.toString()
        ));
        report.put("summary", Map.of(
            "totalEvents", 0,
            "criticalEvents", 0,
            "failedAuthentications", 0,
            "privilegeEscalationAttempts", 0
        ));
        
        return Mono.just(report);
    }
}