package com.alyx.gateway.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Role entity
 * Tests entity constraints, validation rules, and business methods
 */
class RoleTest {

    private Role testRole;
    private List<String> testPermissions;

    @BeforeEach
    void setUp() {
        testPermissions = Arrays.asList("READ_DATA", "SUBMIT_JOBS", "VIEW_RESULTS");
        testRole = new Role("PHYSICIST", "Physics researcher with analysis access", 
                           testPermissions, 3);
    }

    @Test
    void testRoleCreation() {
        assertNotNull(testRole);
        assertEquals("PHYSICIST", testRole.getName());
        assertEquals("Physics researcher with analysis access", testRole.getDescription());
        assertEquals(testPermissions, testRole.getPermissions());
        assertEquals(3, testRole.getHierarchyLevel());
    }

    @Test
    void testDefaultConstructor() {
        Role emptyRole = new Role();
        assertNotNull(emptyRole);
        assertNull(emptyRole.getName());
        assertNull(emptyRole.getDescription());
        assertNull(emptyRole.getPermissions());
        assertNull(emptyRole.getHierarchyLevel());
    }

    @Test
    void testFromUserRole() {
        Role adminRole = Role.fromUserRole(UserRole.ADMIN);
        assertEquals("ADMIN", adminRole.getName());
        assertEquals(UserRole.ADMIN.getDescription(), adminRole.getDescription());
        assertEquals(4, adminRole.getHierarchyLevel());
        assertNotNull(adminRole.getPermissions());
        assertTrue(adminRole.getPermissions().size() > 0);
        
        Role guestRole = Role.fromUserRole(UserRole.GUEST);
        assertEquals("GUEST", guestRole.getName());
        assertEquals(UserRole.GUEST.getDescription(), guestRole.getDescription());
        assertEquals(1, guestRole.getHierarchyLevel());
    }

    @Test
    void testToUserRole() {
        testRole.setName("ADMIN");
        assertEquals(UserRole.ADMIN, testRole.toUserRole());
        
        testRole.setName("PHYSICIST");
        assertEquals(UserRole.PHYSICIST, testRole.toUserRole());
        
        testRole.setName("ANALYST");
        assertEquals(UserRole.ANALYST, testRole.toUserRole());
        
        testRole.setName("GUEST");
        assertEquals(UserRole.GUEST, testRole.toUserRole());
        
        // Test invalid role name
        testRole.setName("INVALID_ROLE");
        assertEquals(UserRole.GUEST, testRole.toUserRole());
    }

    @Test
    void testHasPermissionWithString() {
        assertTrue(testRole.hasPermission("READ_DATA"));
        assertTrue(testRole.hasPermission("SUBMIT_JOBS"));
        assertTrue(testRole.hasPermission("VIEW_RESULTS"));
        assertFalse(testRole.hasPermission("USER_MANAGEMENT"));
        assertFalse(testRole.hasPermission("NONEXISTENT_PERMISSION"));
    }

    @Test
    void testHasPermissionWithEnum() {
        assertTrue(testRole.hasPermission(Permission.READ_DATA));
        assertTrue(testRole.hasPermission(Permission.SUBMIT_JOBS));
        assertTrue(testRole.hasPermission(Permission.VIEW_RESULTS));
        assertFalse(testRole.hasPermission(Permission.USER_MANAGEMENT));
    }

    @Test
    void testHasPermissionWithNullPermissions() {
        testRole.setPermissions(null);
        assertFalse(testRole.hasPermission("READ_DATA"));
        assertFalse(testRole.hasPermission(Permission.READ_DATA));
    }

    @Test
    void testHasPermissionWithEmptyPermissions() {
        testRole.setPermissions(List.of());
        assertFalse(testRole.hasPermission("READ_DATA"));
        assertFalse(testRole.hasPermission(Permission.READ_DATA));
    }

    @Test
    void testPermissionModification() {
        // Add new permission
        List<String> newPermissions = Arrays.asList("READ_DATA", "SUBMIT_JOBS", "VIEW_RESULTS", "DELETE_DATA");
        testRole.setPermissions(newPermissions);
        assertTrue(testRole.hasPermission("DELETE_DATA"));
        assertEquals(4, testRole.getPermissions().size());
        
        // Remove permission
        newPermissions = Arrays.asList("READ_DATA", "SUBMIT_JOBS");
        testRole.setPermissions(newPermissions);
        assertFalse(testRole.hasPermission("VIEW_RESULTS"));
        assertEquals(2, testRole.getPermissions().size());
    }

    @Test
    void testHierarchyLevel() {
        assertEquals(3, testRole.getHierarchyLevel());
        
        testRole.setHierarchyLevel(5);
        assertEquals(5, testRole.getHierarchyLevel());
        
        testRole.setHierarchyLevel(1);
        assertEquals(1, testRole.getHierarchyLevel());
    }

    @Test
    void testNameConstraints() {
        // Test valid names
        testRole.setName("ADMIN");
        assertEquals("ADMIN", testRole.getName());
        
        testRole.setName("CUSTOM_ROLE");
        assertEquals("CUSTOM_ROLE", testRole.getName());
        
        // Test maximum length name (50 characters)
        String longName = "a".repeat(50);
        testRole.setName(longName);
        assertEquals(longName, testRole.getName());
    }

    @Test
    void testDescriptionHandling() {
        // Test normal description
        testRole.setDescription("Updated description");
        assertEquals("Updated description", testRole.getDescription());
        
        // Test null description
        testRole.setDescription(null);
        assertNull(testRole.getDescription());
        
        // Test empty description
        testRole.setDescription("");
        assertEquals("", testRole.getDescription());
        
        // Test long description
        String longDescription = "a".repeat(1000);
        testRole.setDescription(longDescription);
        assertEquals(longDescription, testRole.getDescription());
    }

    @Test
    void testEqualsAndHashCode() {
        Role role1 = new Role();
        Role role2 = new Role();
        
        // Without IDs, should not be equal
        assertNotEquals(role1, role2);
        
        // With same ID, should be equal
        UUID id = UUID.randomUUID();
        role1.setId(id);
        role2.setId(id);
        assertEquals(role1, role2);
        assertEquals(role1.hashCode(), role2.hashCode());
        
        // With different IDs, should not be equal
        role2.setId(UUID.randomUUID());
        assertNotEquals(role1, role2);
    }

    @Test
    void testToString() {
        testRole.setId(UUID.randomUUID());
        String toString = testRole.toString();
        
        assertTrue(toString.contains("Role{"));
        assertTrue(toString.contains("name='PHYSICIST'"));
        assertTrue(toString.contains("hierarchyLevel=3"));
        assertTrue(toString.contains("id="));
    }

    @Test
    void testRoleComparison() {
        Role adminRole = Role.fromUserRole(UserRole.ADMIN);
        Role physicistRole = Role.fromUserRole(UserRole.PHYSICIST);
        Role analystRole = Role.fromUserRole(UserRole.ANALYST);
        Role guestRole = Role.fromUserRole(UserRole.GUEST);
        
        // Test hierarchy levels
        assertTrue(adminRole.getHierarchyLevel() > physicistRole.getHierarchyLevel());
        assertTrue(physicistRole.getHierarchyLevel() > analystRole.getHierarchyLevel());
        assertTrue(analystRole.getHierarchyLevel() > guestRole.getHierarchyLevel());
    }

    @Test
    void testPermissionInheritance() {
        Role adminRole = Role.fromUserRole(UserRole.ADMIN);
        Role physicistRole = Role.fromUserRole(UserRole.PHYSICIST);
        Role analystRole = Role.fromUserRole(UserRole.ANALYST);
        Role guestRole = Role.fromUserRole(UserRole.GUEST);
        
        // Admin should have more permissions than others
        assertTrue(adminRole.getPermissions().size() >= physicistRole.getPermissions().size());
        
        // Guest should have the fewest permissions
        assertTrue(guestRole.getPermissions().size() <= analystRole.getPermissions().size());
        assertTrue(guestRole.getPermissions().size() <= physicistRole.getPermissions().size());
        assertTrue(guestRole.getPermissions().size() <= adminRole.getPermissions().size());
    }

    @Test
    void testCreatedAtTimestamp() {
        // CreatedAt should be set automatically by @CreationTimestamp
        // This test verifies the field exists and can be set manually for testing
        assertNull(testRole.getCreatedAt()); // Not set until persisted
        
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        testRole.setCreatedAt(now);
        assertEquals(now, testRole.getCreatedAt());
    }
}