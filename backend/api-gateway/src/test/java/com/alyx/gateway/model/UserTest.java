package com.alyx.gateway.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for User entity
 * Tests entity constraints, validation rules, and business methods
 */
class UserTest {

    private Role testRole;
    private User testUser;

    @BeforeEach
    void setUp() {
        // Create test role
        testRole = new Role("PHYSICIST", "Test physicist role", 
                           List.of("READ_DATA", "SUBMIT_JOBS"), 3);
        testRole.setId(UUID.randomUUID());
        
        // Create test user
        testUser = new User("test@example.com", "hashedPassword123", 
                           "John", "Doe", "CERN", testRole);
    }

    @Test
    void testUserCreation() {
        assertNotNull(testUser);
        assertEquals("test@example.com", testUser.getEmail());
        assertEquals("hashedPassword123", testUser.getPasswordHash());
        assertEquals("John", testUser.getFirstName());
        assertEquals("Doe", testUser.getLastName());
        assertEquals("CERN", testUser.getOrganization());
        assertEquals(testRole, testUser.getRole());
    }

    @Test
    void testDefaultValues() {
        User newUser = new User();
        newUser.onCreate(); // Simulate @PrePersist
        
        assertTrue(newUser.getIsActive());
        assertFalse(newUser.getEmailVerified());
        assertEquals(0, newUser.getFailedLoginAttempts());
    }

    @Test
    void testFullName() {
        assertEquals("John Doe", testUser.getFullName());
        
        testUser.setFirstName("Jane");
        testUser.setLastName("Smith");
        assertEquals("Jane Smith", testUser.getFullName());
    }

    @Test
    void testAccountLocking() {
        // Initially not locked
        assertFalse(testUser.isAccountLocked());
        
        // Lock account for 15 minutes
        testUser.lockAccount(15);
        assertTrue(testUser.isAccountLocked());
        assertNotNull(testUser.getLockedUntil());
        assertTrue(testUser.getLockedUntil().isAfter(LocalDateTime.now()));
        
        // Set lock time in the past
        testUser.setLockedUntil(LocalDateTime.now().minusMinutes(1));
        assertFalse(testUser.isAccountLocked());
    }

    @Test
    void testFailedLoginAttempts() {
        assertEquals(0, testUser.getFailedLoginAttempts());
        
        // Increment failed attempts
        testUser.incrementFailedLoginAttempts();
        assertEquals(1, testUser.getFailedLoginAttempts());
        
        testUser.incrementFailedLoginAttempts();
        assertEquals(2, testUser.getFailedLoginAttempts());
        
        // Reset failed attempts
        testUser.resetFailedLoginAttempts();
        assertEquals(0, testUser.getFailedLoginAttempts());
        assertNull(testUser.getLockedUntil());
    }

    @Test
    void testFailedLoginAttemptsWithNullValue() {
        testUser.setFailedLoginAttempts(null);
        testUser.incrementFailedLoginAttempts();
        assertEquals(1, testUser.getFailedLoginAttempts());
    }

    @Test
    void testPermissionChecking() {
        // Test with Permission enum
        assertTrue(testUser.hasPermission(Permission.READ_DATA));
        assertTrue(testUser.hasPermission(Permission.SUBMIT_JOBS));
        assertFalse(testUser.hasPermission(Permission.USER_MANAGEMENT));
        
        // Test with string permission
        assertTrue(testUser.hasPermission("READ_DATA"));
        assertTrue(testUser.hasPermission("SUBMIT_JOBS"));
        assertFalse(testUser.hasPermission("USER_MANAGEMENT"));
    }

    @Test
    void testPermissionCheckingWithNullRole() {
        testUser.setRole(null);
        assertFalse(testUser.hasPermission(Permission.READ_DATA));
        assertFalse(testUser.hasPermission("READ_DATA"));
    }

    @Test
    void testUpdateLastLogin() {
        assertNull(testUser.getLastLoginAt());
        
        testUser.updateLastLogin();
        assertNotNull(testUser.getLastLoginAt());
        assertTrue(testUser.getLastLoginAt().isBefore(LocalDateTime.now().plusSeconds(1)));
        assertTrue(testUser.getLastLoginAt().isAfter(LocalDateTime.now().minusSeconds(1)));
    }

    @Test
    void testEqualsAndHashCode() {
        User user1 = new User();
        User user2 = new User();
        
        // Without IDs, should not be equal
        assertNotEquals(user1, user2);
        
        // With same ID, should be equal
        UUID id = UUID.randomUUID();
        user1.setId(id);
        user2.setId(id);
        assertEquals(user1, user2);
        assertEquals(user1.hashCode(), user2.hashCode());
        
        // With different IDs, should not be equal
        user2.setId(UUID.randomUUID());
        assertNotEquals(user1, user2);
    }

    @Test
    void testToString() {
        testUser.setId(UUID.randomUUID());
        String toString = testUser.toString();
        
        assertTrue(toString.contains("User{"));
        assertTrue(toString.contains("email='test@example.com'"));
        assertTrue(toString.contains("firstName='John'"));
        assertTrue(toString.contains("lastName='Doe'"));
        assertTrue(toString.contains("organization='CERN'"));
        assertTrue(toString.contains("isActive=true"));
    }

    @Test
    void testEmailValidation() {
        // Valid emails should work
        testUser.setEmail("valid@example.com");
        assertEquals("valid@example.com", testUser.getEmail());
        
        testUser.setEmail("user.name+tag@domain.co.uk");
        assertEquals("user.name+tag@domain.co.uk", testUser.getEmail());
    }

    @Test
    void testNameLengthConstraints() {
        // Test maximum length names
        String longName = "a".repeat(100);
        testUser.setFirstName(longName);
        testUser.setLastName(longName);
        assertEquals(longName, testUser.getFirstName());
        assertEquals(longName, testUser.getLastName());
    }

    @Test
    void testOrganizationConstraints() {
        // Test maximum length organization
        String longOrg = "a".repeat(100);
        testUser.setOrganization(longOrg);
        assertEquals(longOrg, testUser.getOrganization());
        
        // Test null organization
        testUser.setOrganization(null);
        assertNull(testUser.getOrganization());
    }

    @Test
    void testAccountActivation() {
        assertTrue(testUser.getIsActive());
        
        testUser.setIsActive(false);
        assertFalse(testUser.getIsActive());
        
        testUser.setIsActive(true);
        assertTrue(testUser.getIsActive());
    }

    @Test
    void testEmailVerification() {
        assertFalse(testUser.getEmailVerified());
        
        testUser.setEmailVerified(true);
        assertTrue(testUser.getEmailVerified());
        
        testUser.setEmailVerified(false);
        assertFalse(testUser.getEmailVerified());
    }
}