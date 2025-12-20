package com.alyx.gateway.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for UserRole enum
 */
class UserRoleTest {

    @Test
    void testRoleHierarchy() {
        // Test hierarchy levels
        assertEquals(4, UserRole.ADMIN.getHierarchyLevel());
        assertEquals(3, UserRole.PHYSICIST.getHierarchyLevel());
        assertEquals(2, UserRole.ANALYST.getHierarchyLevel());
        assertEquals(1, UserRole.GUEST.getHierarchyLevel());
        
        // Test hierarchy ordering
        assertTrue(UserRole.ADMIN.getHierarchyLevel() > UserRole.PHYSICIST.getHierarchyLevel());
        assertTrue(UserRole.PHYSICIST.getHierarchyLevel() > UserRole.ANALYST.getHierarchyLevel());
        assertTrue(UserRole.ANALYST.getHierarchyLevel() > UserRole.GUEST.getHierarchyLevel());
    }

    @Test
    void testAdminPermissions() {
        // Admin should have all permissions
        assertTrue(UserRole.ADMIN.hasPermission(Permission.USER_MANAGEMENT));
        assertTrue(UserRole.ADMIN.hasPermission(Permission.SYSTEM_CONFIG));
        assertTrue(UserRole.ADMIN.hasPermission(Permission.SUBMIT_JOBS));
        assertTrue(UserRole.ADMIN.hasPermission(Permission.READ_DATA));
        assertTrue(UserRole.ADMIN.hasPermission(Permission.DELETE_DATA));
        assertTrue(UserRole.ADMIN.hasPermission(Permission.VIEW_METRICS));
    }

    @Test
    void testPhysicistPermissions() {
        // Physicist should have most permissions except admin-specific ones
        assertFalse(UserRole.PHYSICIST.hasPermission(Permission.USER_MANAGEMENT));
        assertFalse(UserRole.PHYSICIST.hasPermission(Permission.SYSTEM_CONFIG));
        assertTrue(UserRole.PHYSICIST.hasPermission(Permission.SUBMIT_JOBS));
        assertTrue(UserRole.PHYSICIST.hasPermission(Permission.READ_DATA));
        assertTrue(UserRole.PHYSICIST.hasPermission(Permission.CREATE_NOTEBOOKS));
        assertTrue(UserRole.PHYSICIST.hasPermission(Permission.ADVANCED_VISUALIZATION));
    }

    @Test
    void testAnalystPermissions() {
        // Analyst should have limited permissions
        assertFalse(UserRole.ANALYST.hasPermission(Permission.USER_MANAGEMENT));
        assertFalse(UserRole.ANALYST.hasPermission(Permission.SYSTEM_CONFIG));
        assertFalse(UserRole.ANALYST.hasPermission(Permission.SUBMIT_JOBS));
        assertTrue(UserRole.ANALYST.hasPermission(Permission.READ_DATA));
        assertTrue(UserRole.ANALYST.hasPermission(Permission.VIEW_RESULTS));
        assertTrue(UserRole.ANALYST.hasPermission(Permission.BASIC_VISUALIZATION));
        assertFalse(UserRole.ANALYST.hasPermission(Permission.ADVANCED_VISUALIZATION));
    }

    @Test
    void testGuestPermissions() {
        // Guest should have very limited permissions
        assertFalse(UserRole.GUEST.hasPermission(Permission.USER_MANAGEMENT));
        assertFalse(UserRole.GUEST.hasPermission(Permission.SYSTEM_CONFIG));
        assertFalse(UserRole.GUEST.hasPermission(Permission.SUBMIT_JOBS));
        assertFalse(UserRole.GUEST.hasPermission(Permission.READ_DATA));
        assertTrue(UserRole.GUEST.hasPermission(Permission.READ_PUBLIC_DATA));
        assertFalse(UserRole.GUEST.hasPermission(Permission.VIEW_RESULTS));
    }

    @Test
    void testFromString() {
        // Test valid role strings
        assertEquals(UserRole.ADMIN, UserRole.fromString("ADMIN"));
        assertEquals(UserRole.ADMIN, UserRole.fromString("admin"));
        assertEquals(UserRole.PHYSICIST, UserRole.fromString("PHYSICIST"));
        assertEquals(UserRole.PHYSICIST, UserRole.fromString("physicist"));
        assertEquals(UserRole.ANALYST, UserRole.fromString("ANALYST"));
        assertEquals(UserRole.GUEST, UserRole.fromString("GUEST"));
        
        // Test invalid role strings
        assertEquals(UserRole.GUEST, UserRole.fromString("INVALID_ROLE"));
        assertEquals(UserRole.GUEST, UserRole.fromString(null));
        assertEquals(UserRole.GUEST, UserRole.fromString(""));
    }

    @Test
    void testRoleNames() {
        assertEquals("ADMIN", UserRole.ADMIN.getRoleName());
        assertEquals("PHYSICIST", UserRole.PHYSICIST.getRoleName());
        assertEquals("ANALYST", UserRole.ANALYST.getRoleName());
        assertEquals("GUEST", UserRole.GUEST.getRoleName());
    }

    @Test
    void testRoleDescriptions() {
        assertNotNull(UserRole.ADMIN.getDescription());
        assertNotNull(UserRole.PHYSICIST.getDescription());
        assertNotNull(UserRole.ANALYST.getDescription());
        assertNotNull(UserRole.GUEST.getDescription());
        
        assertTrue(UserRole.ADMIN.getDescription().contains("Full system access"));
        assertTrue(UserRole.PHYSICIST.getDescription().contains("analysis access"));
        assertTrue(UserRole.ANALYST.getDescription().contains("Read-only"));
        assertTrue(UserRole.GUEST.getDescription().contains("Limited"));
    }
}