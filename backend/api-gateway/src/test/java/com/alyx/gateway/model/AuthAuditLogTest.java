package com.alyx.gateway.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AuthAuditLog entity
 * Tests entity constraints, validation rules, and factory methods
 */
class AuthAuditLogTest {

    private User testUser;
    private Role testRole;
    private InetAddress testIpAddress;
    private String testUserAgent;

    @BeforeEach
    void setUp() throws UnknownHostException {
        // Create test role and user
        testRole = new Role("PHYSICIST", "Test physicist role", 
                           List.of("READ_DATA", "SUBMIT_JOBS"), 3);
        testRole.setId(UUID.randomUUID());
        
        testUser = new User("test@example.com", "hashedPassword123", 
                           "John", "Doe", "CERN", testRole);
        testUser.setId(UUID.randomUUID());
        
        testIpAddress = InetAddress.getByName("192.168.1.100");
        testUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
    }

    @Test
    void testAuditLogCreation() {
        Map<String, Object> eventDetails = Map.of("key1", "value1", "key2", 42);
        
        AuthAuditLog auditLog = new AuthAuditLog(testUser, "LOGIN_SUCCESS", eventDetails, 
                                                testIpAddress, testUserAgent, true);
        
        assertNotNull(auditLog);
        assertEquals(testUser, auditLog.getUser());
        assertEquals("LOGIN_SUCCESS", auditLog.getEventType());
        assertEquals(eventDetails, auditLog.getEventDetails());
        assertEquals(testIpAddress, auditLog.getIpAddress());
        assertEquals(testUserAgent, auditLog.getUserAgent());
        assertTrue(auditLog.getSuccess());
    }

    @Test
    void testDefaultConstructor() {
        AuthAuditLog auditLog = new AuthAuditLog();
        assertNotNull(auditLog);
        assertNull(auditLog.getUser());
        assertNull(auditLog.getEventType());
        assertNull(auditLog.getEventDetails());
        assertNull(auditLog.getIpAddress());
        assertNull(auditLog.getUserAgent());
        assertNull(auditLog.getSuccess());
    }

    @Test
    void testLoginSuccessFactory() {
        AuthAuditLog auditLog = AuthAuditLog.loginSuccess(testUser, testIpAddress, testUserAgent);
        
        assertEquals(testUser, auditLog.getUser());
        assertEquals("LOGIN_SUCCESS", auditLog.getEventType());
        assertNull(auditLog.getEventDetails());
        assertEquals(testIpAddress, auditLog.getIpAddress());
        assertEquals(testUserAgent, auditLog.getUserAgent());
        assertTrue(auditLog.getSuccess());
    }

    @Test
    void testLoginFailureFactory() {
        String email = "test@example.com";
        String reason = "Invalid password";
        
        AuthAuditLog auditLog = AuthAuditLog.loginFailure(email, testIpAddress, testUserAgent, reason);
        
        assertNull(auditLog.getUser()); // No user for failed login
        assertEquals("LOGIN_FAILURE", auditLog.getEventType());
        assertNotNull(auditLog.getEventDetails());
        assertEquals(email, auditLog.getEventDetails().get("email"));
        assertEquals(reason, auditLog.getEventDetails().get("failure_reason"));
        assertEquals(testIpAddress, auditLog.getIpAddress());
        assertEquals(testUserAgent, auditLog.getUserAgent());
        assertFalse(auditLog.getSuccess());
    }

    @Test
    void testRegistrationSuccessFactory() {
        AuthAuditLog auditLog = AuthAuditLog.registrationSuccess(testUser, testIpAddress, testUserAgent);
        
        assertEquals(testUser, auditLog.getUser());
        assertEquals("REGISTRATION_SUCCESS", auditLog.getEventType());
        assertNull(auditLog.getEventDetails());
        assertEquals(testIpAddress, auditLog.getIpAddress());
        assertEquals(testUserAgent, auditLog.getUserAgent());
        assertTrue(auditLog.getSuccess());
    }

    @Test
    void testRegistrationFailureFactory() {
        String email = "invalid@example.com";
        String reason = "Email already exists";
        
        AuthAuditLog auditLog = AuthAuditLog.registrationFailure(email, testIpAddress, testUserAgent, reason);
        
        assertNull(auditLog.getUser()); // No user for failed registration
        assertEquals("REGISTRATION_FAILURE", auditLog.getEventType());
        assertNotNull(auditLog.getEventDetails());
        assertEquals(email, auditLog.getEventDetails().get("email"));
        assertEquals(reason, auditLog.getEventDetails().get("failure_reason"));
        assertEquals(testIpAddress, auditLog.getIpAddress());
        assertEquals(testUserAgent, auditLog.getUserAgent());
        assertFalse(auditLog.getSuccess());
    }

    @Test
    void testLogoutFactory() {
        AuthAuditLog auditLog = AuthAuditLog.logout(testUser, testIpAddress, testUserAgent);
        
        assertEquals(testUser, auditLog.getUser());
        assertEquals("LOGOUT", auditLog.getEventType());
        assertNull(auditLog.getEventDetails());
        assertEquals(testIpAddress, auditLog.getIpAddress());
        assertEquals(testUserAgent, auditLog.getUserAgent());
        assertTrue(auditLog.getSuccess());
    }

    @Test
    void testAccountLockedFactory() {
        testUser.setFailedLoginAttempts(5);
        LocalDateTime lockUntil = LocalDateTime.now().plusMinutes(15);
        testUser.setLockedUntil(lockUntil);
        
        AuthAuditLog auditLog = AuthAuditLog.accountLocked(testUser, testIpAddress, testUserAgent);
        
        assertEquals(testUser, auditLog.getUser());
        assertEquals("ACCOUNT_LOCKED", auditLog.getEventType());
        assertNotNull(auditLog.getEventDetails());
        assertEquals(5, auditLog.getEventDetails().get("failed_attempts"));
        assertEquals(lockUntil, auditLog.getEventDetails().get("locked_until"));
        assertEquals(testIpAddress, auditLog.getIpAddress());
        assertEquals(testUserAgent, auditLog.getUserAgent());
        assertFalse(auditLog.getSuccess());
    }

    @Test
    void testSuspiciousActivityFactory() {
        String activity = "Multiple rapid login attempts";
        
        AuthAuditLog auditLog = AuthAuditLog.suspiciousActivity(testUser, testIpAddress, testUserAgent, activity);
        
        assertEquals(testUser, auditLog.getUser());
        assertEquals("SUSPICIOUS_ACTIVITY", auditLog.getEventType());
        assertNotNull(auditLog.getEventDetails());
        assertEquals(activity, auditLog.getEventDetails().get("activity_type"));
        assertEquals(testIpAddress, auditLog.getIpAddress());
        assertEquals(testUserAgent, auditLog.getUserAgent());
        assertFalse(auditLog.getSuccess());
    }

    @Test
    void testEventDetailsHandling() {
        AuthAuditLog auditLog = new AuthAuditLog();
        
        // Test null event details
        auditLog.setEventDetails(null);
        assertNull(auditLog.getEventDetails());
        
        // Test empty event details
        Map<String, Object> emptyDetails = Map.of();
        auditLog.setEventDetails(emptyDetails);
        assertEquals(emptyDetails, auditLog.getEventDetails());
        
        // Test complex event details
        Map<String, Object> complexDetails = Map.of(
            "string_value", "test",
            "number_value", 123,
            "boolean_value", true,
            "nested_object", Map.of("inner_key", "inner_value")
        );
        auditLog.setEventDetails(complexDetails);
        assertEquals(complexDetails, auditLog.getEventDetails());
    }

    @Test
    void testIpAddressHandling() throws UnknownHostException {
        AuthAuditLog auditLog = new AuthAuditLog();
        
        // Test IPv4 address
        InetAddress ipv4 = InetAddress.getByName("192.168.1.1");
        auditLog.setIpAddress(ipv4);
        assertEquals(ipv4, auditLog.getIpAddress());
        
        // Test IPv6 address
        InetAddress ipv6 = InetAddress.getByName("2001:db8::1");
        auditLog.setIpAddress(ipv6);
        assertEquals(ipv6, auditLog.getIpAddress());
        
        // Test localhost
        InetAddress localhost = InetAddress.getByName("127.0.0.1");
        auditLog.setIpAddress(localhost);
        assertEquals(localhost, auditLog.getIpAddress());
        
        // Test null IP address
        auditLog.setIpAddress(null);
        assertNull(auditLog.getIpAddress());
    }

    @Test
    void testUserAgentHandling() {
        AuthAuditLog auditLog = new AuthAuditLog();
        
        // Test normal user agent
        String normalUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
        auditLog.setUserAgent(normalUserAgent);
        assertEquals(normalUserAgent, auditLog.getUserAgent());
        
        // Test empty user agent
        auditLog.setUserAgent("");
        assertEquals("", auditLog.getUserAgent());
        
        // Test null user agent
        auditLog.setUserAgent(null);
        assertNull(auditLog.getUserAgent());
        
        // Test very long user agent
        String longUserAgent = "a".repeat(1000);
        auditLog.setUserAgent(longUserAgent);
        assertEquals(longUserAgent, auditLog.getUserAgent());
    }

    @Test
    void testEqualsAndHashCode() {
        AuthAuditLog log1 = new AuthAuditLog();
        AuthAuditLog log2 = new AuthAuditLog();
        
        // Without IDs, should not be equal
        assertNotEquals(log1, log2);
        
        // With same ID, should be equal
        UUID id = UUID.randomUUID();
        log1.setId(id);
        log2.setId(id);
        assertEquals(log1, log2);
        assertEquals(log1.hashCode(), log2.hashCode());
        
        // With different IDs, should not be equal
        log2.setId(UUID.randomUUID());
        assertNotEquals(log1, log2);
    }

    @Test
    void testToString() {
        AuthAuditLog auditLog = AuthAuditLog.loginSuccess(testUser, testIpAddress, testUserAgent);
        auditLog.setId(UUID.randomUUID());
        
        String toString = auditLog.toString();
        
        assertTrue(toString.contains("AuthAuditLog{"));
        assertTrue(toString.contains("eventType='LOGIN_SUCCESS'"));
        assertTrue(toString.contains("success=true"));
        assertTrue(toString.contains("id="));
    }

    @Test
    void testCreatedAtTimestamp() {
        AuthAuditLog auditLog = new AuthAuditLog();
        
        // CreatedAt should be set automatically by @CreationTimestamp
        // This test verifies the field exists and can be set manually for testing
        assertNull(auditLog.getCreatedAt()); // Not set until persisted
        
        LocalDateTime now = LocalDateTime.now();
        auditLog.setCreatedAt(now);
        assertEquals(now, auditLog.getCreatedAt());
    }

    @Test
    void testEventTypeValidation() {
        AuthAuditLog auditLog = new AuthAuditLog();
        
        // Test valid event types
        auditLog.setEventType("LOGIN_SUCCESS");
        assertEquals("LOGIN_SUCCESS", auditLog.getEventType());
        
        auditLog.setEventType("LOGIN_FAILURE");
        assertEquals("LOGIN_FAILURE", auditLog.getEventType());
        
        auditLog.setEventType("REGISTRATION_SUCCESS");
        assertEquals("REGISTRATION_SUCCESS", auditLog.getEventType());
        
        auditLog.setEventType("CUSTOM_EVENT");
        assertEquals("CUSTOM_EVENT", auditLog.getEventType());
    }

    @Test
    void testSuccessStatusHandling() {
        AuthAuditLog auditLog = new AuthAuditLog();
        
        // Test true success
        auditLog.setSuccess(true);
        assertTrue(auditLog.getSuccess());
        
        // Test false success
        auditLog.setSuccess(false);
        assertFalse(auditLog.getSuccess());
        
        // Test null success (should be handled by validation)
        auditLog.setSuccess(null);
        assertNull(auditLog.getSuccess());
    }
}