package com.alyx.gateway.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveZSetOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for Security Audit Service
 */
@ExtendWith(MockitoExtension.class)
class SecurityAuditServiceTest {

    @Mock
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @Mock
    private ReactiveZSetOperations<String, String> zSetOperations;

    private SecurityAuditService auditService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.add(anyString(), anyString(), anyDouble())).thenReturn(Mono.just(true));
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));
        when(zSetOperations.count(anyString(), anyDouble(), anyDouble())).thenReturn(Mono.just(0L));

        auditService = new SecurityAuditService(redisTemplate);
    }

    @Test
    void testLogSecurityEvent() {
        // Given
        String eventType = "LOGIN_SUCCESS";
        String resource = "/api/auth/login";
        String userId = "test-user";
        String details = "Successful login";

        // When & Then - Should not throw any exceptions
        assertDoesNotThrow(() -> 
            auditService.logSecurityEvent(eventType, resource, userId, details)
        );
    }

    @Test
    void testLogAuthenticationFailure() {
        // Given
        String userId = "test-user";
        String ipAddress = "192.168.1.1";
        String userAgent = "Mozilla/5.0";
        String reason = "Invalid password";

        // When & Then - Should not throw any exceptions
        assertDoesNotThrow(() -> 
            auditService.logAuthenticationFailure(userId, ipAddress, userAgent, reason)
        );
    }

    @Test
    void testLogPrivilegeEscalation() {
        // Given
        String userId = "test-user";
        String resource = "/api/admin/users";
        String currentRole = "ANALYST";
        String requiredRole = "ADMIN";

        // When & Then - Should not throw any exceptions
        assertDoesNotThrow(() -> 
            auditService.logPrivilegeEscalation(userId, resource, currentRole, requiredRole)
        );
    }

    @Test
    void testLogDataAccess() {
        // Given
        String userId = "test-user";
        String dataType = "collision-events";
        String operation = "READ";
        boolean success = true;

        // When & Then - Should not throw any exceptions
        assertDoesNotThrow(() -> 
            auditService.logDataAccess(userId, dataType, operation, success)
        );
    }

    @Test
    void testLogSuspiciousActivity() {
        // Given
        String userId = "test-user";
        String ipAddress = "192.168.1.1";
        String activity = "Multiple failed login attempts";
        String riskLevel = "HIGH";

        // When & Then - Should not throw any exceptions
        assertDoesNotThrow(() -> 
            auditService.logSuspiciousActivity(userId, ipAddress, activity, riskLevel)
        );
    }

    @Test
    void testGenerateSecurityReport() {
        // Given
        java.time.Instant startTime = java.time.Instant.now().minusSeconds(3600);
        java.time.Instant endTime = java.time.Instant.now();

        // When
        Mono<java.util.Map<String, Object>> reportMono = auditService.generateSecurityReport(startTime, endTime);

        // Then
        StepVerifier.create(reportMono)
            .expectNextMatches(report -> {
                assertNotNull(report);
                assertTrue(report.containsKey("reportPeriod"));
                assertTrue(report.containsKey("summary"));
                return true;
            })
            .verifyComplete();
    }

    private void assertDoesNotThrow(Runnable runnable) {
        try {
            runnable.run();
        } catch (Exception e) {
            throw new AssertionError("Expected no exception, but got: " + e.getMessage(), e);
        }
    }

    private void assertNotNull(Object object) {
        if (object == null) {
            throw new AssertionError("Expected non-null object");
        }
    }

    private void assertTrue(boolean condition) {
        if (!condition) {
            throw new AssertionError("Expected true, but got false");
        }
    }
}