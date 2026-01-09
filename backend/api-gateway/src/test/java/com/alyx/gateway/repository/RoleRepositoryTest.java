package com.alyx.gateway.repository;

import com.alyx.gateway.model.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RoleRepository
 * Tests custom query methods and repository functionality
 */
@DataJpaTest
@ActiveProfiles("test")
class RoleRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private RoleRepository roleRepository;

    private Role adminRole;
    private Role physicistRole;
    private Role analystRole;
    private Role guestRole;

    @BeforeEach
    void setUp() {
        // Create test roles with different hierarchy levels and permissions
        adminRole = new Role("ADMIN", "Administrator with full access", 
                           Arrays.asList("USER_MANAGEMENT", "SYSTEM_CONFIG", "READ_DATA", "WRITE_DATA"), 4);
        physicistRole = new Role("PHYSICIST", "Physicist with analysis access", 
                               Arrays.asList("READ_DATA", "SUBMIT_JOBS", "VIEW_RESULTS"), 3);
        analystRole = new Role("ANALYST", "Analyst with read-only access", 
                             Arrays.asList("READ_DATA", "VIEW_RESULTS"), 2);
        guestRole = new Role("GUEST", "Guest with limited access", 
                           Arrays.asList("READ_PUBLIC_DATA"), 1);

        // Persist roles
        adminRole = entityManager.persistAndFlush(adminRole);
        physicistRole = entityManager.persistAndFlush(physicistRole);
        analystRole = entityManager.persistAndFlush(analystRole);
        guestRole = entityManager.persistAndFlush(guestRole);
    }

    @Test
    void testFindByName() {
        Optional<Role> found = roleRepository.findByName("PHYSICIST");
        assertTrue(found.isPresent());
        assertEquals(physicistRole.getId(), found.get().getId());
        assertEquals("PHYSICIST", found.get().getName());
    }

    @Test
    void testFindByNameCaseInsensitive() {
        Optional<Role> found = roleRepository.findByName("physicist");
        assertTrue(found.isPresent());
        assertEquals(physicistRole.getId(), found.get().getId());
        
        found = roleRepository.findByName("PhYsIcIsT");
        assertTrue(found.isPresent());
        assertEquals(physicistRole.getId(), found.get().getId());
    }

    @Test
    void testFindByNameNotFound() {
        Optional<Role> found = roleRepository.findByName("NONEXISTENT");
        assertFalse(found.isPresent());
    }

    @Test
    void testExistsByName() {
        assertTrue(roleRepository.existsByName("ADMIN"));
        assertTrue(roleRepository.existsByName("admin"));
        assertFalse(roleRepository.existsByName("NONEXISTENT"));
    }

    @Test
    void testFindAllOrderedByHierarchy() {
        List<Role> roles = roleRepository.findAllOrderedByHierarchy();
        assertEquals(4, roles.size());
        
        // Should be ordered by hierarchy level descending (highest first)
        assertEquals(adminRole.getId(), roles.get(0).getId());
        assertEquals(physicistRole.getId(), roles.get(1).getId());
        assertEquals(analystRole.getId(), roles.get(2).getId());
        assertEquals(guestRole.getId(), roles.get(3).getId());
    }

    @Test
    void testFindRolesWithMinimumLevel() {
        List<Role> roles = roleRepository.findRolesWithMinimumLevel(2);
        assertEquals(3, roles.size()); // admin, physicist, analyst
        
        roles = roleRepository.findRolesWithMinimumLevel(4);
        assertEquals(1, roles.size()); // only admin
        assertEquals(adminRole.getId(), roles.get(0).getId());
        
        roles = roleRepository.findRolesWithMinimumLevel(5);
        assertEquals(0, roles.size()); // none
    }

    @Test
    void testFindRolesWithMaximumLevel() {
        List<Role> roles = roleRepository.findRolesWithMaximumLevel(2);
        assertEquals(2, roles.size()); // analyst, guest
        
        roles = roleRepository.findRolesWithMaximumLevel(1);
        assertEquals(1, roles.size()); // only guest
        assertEquals(guestRole.getId(), roles.get(0).getId());
        
        roles = roleRepository.findRolesWithMaximumLevel(0);
        assertEquals(0, roles.size()); // none
    }

    @Test
    void testFindRolesWithPermission() {
        List<Role> roles = roleRepository.findRolesWithPermission("READ_DATA");
        assertEquals(3, roles.size()); // admin, physicist, analyst
        
        roles = roleRepository.findRolesWithPermission("USER_MANAGEMENT");
        assertEquals(1, roles.size()); // only admin
        assertEquals(adminRole.getId(), roles.get(0).getId());
        
        roles = roleRepository.findRolesWithPermission("NONEXISTENT_PERMISSION");
        assertEquals(0, roles.size());
    }

    @Test
    void testFindRolesWithAnyPermission() {
        List<String> permissions = Arrays.asList("READ_DATA", "USER_MANAGEMENT");
        List<Role> roles = roleRepository.findRolesWithAnyPermission(permissions);
        assertEquals(3, roles.size()); // admin, physicist, analyst (all have READ_DATA)
        
        permissions = Arrays.asList("USER_MANAGEMENT", "SYSTEM_CONFIG");
        roles = roleRepository.findRolesWithAnyPermission(permissions);
        assertEquals(1, roles.size()); // only admin
        assertEquals(adminRole.getId(), roles.get(0).getId());
    }

    @Test
    void testFindRolesWithAllPermissions() {
        List<String> permissions = Arrays.asList("READ_DATA", "VIEW_RESULTS");
        List<Role> roles = roleRepository.findRolesWithAllPermissions(permissions, 2);
        assertEquals(2, roles.size()); // physicist, analyst
        
        permissions = Arrays.asList("USER_MANAGEMENT", "SYSTEM_CONFIG");
        roles = roleRepository.findRolesWithAllPermissions(permissions, 2);
        assertEquals(1, roles.size()); // only admin
        assertEquals(adminRole.getId(), roles.get(0).getId());
        
        permissions = Arrays.asList("READ_DATA", "NONEXISTENT_PERMISSION");
        roles = roleRepository.findRolesWithAllPermissions(permissions, 2);
        assertEquals(0, roles.size()); // none have both
    }

    @Test
    void testFindDefaultRole() {
        Optional<Role> defaultRole = roleRepository.findDefaultRole();
        assertTrue(defaultRole.isPresent());
        assertEquals(guestRole.getId(), defaultRole.get().getId()); // lowest hierarchy level
    }

    @Test
    void testFindHighestRole() {
        Optional<Role> highestRole = roleRepository.findHighestRole();
        assertTrue(highestRole.isPresent());
        assertEquals(adminRole.getId(), highestRole.get().getId()); // highest hierarchy level
    }

    @Test
    void testCountByHierarchyLevel() {
        long count = roleRepository.countByHierarchyLevel(3);
        assertEquals(1, count); // only physicist
        
        count = roleRepository.countByHierarchyLevel(5);
        assertEquals(0, count); // none
    }

    @Test
    void testFindRolesCreatedBetween() {
        LocalDateTime start = LocalDateTime.now().minusDays(1);
        LocalDateTime end = LocalDateTime.now().plusDays(1);
        
        List<Role> roles = roleRepository.findRolesCreatedBetween(start, end);
        assertEquals(4, roles.size()); // all roles created in test setup
        
        // Test with past date range
        LocalDateTime pastStart = LocalDateTime.now().minusDays(10);
        LocalDateTime pastEnd = LocalDateTime.now().minusDays(5);
        List<Role> pastRoles = roleRepository.findRolesCreatedBetween(pastStart, pastEnd);
        assertEquals(0, pastRoles.size());
    }

    @Test
    void testFindAllUniquePermissions() {
        List<String> permissions = roleRepository.findAllUniquePermissions();
        
        // Should contain all unique permissions from all roles
        assertTrue(permissions.contains("READ_DATA"));
        assertTrue(permissions.contains("USER_MANAGEMENT"));
        assertTrue(permissions.contains("SYSTEM_CONFIG"));
        assertTrue(permissions.contains("SUBMIT_JOBS"));
        assertTrue(permissions.contains("VIEW_RESULTS"));
        assertTrue(permissions.contains("WRITE_DATA"));
        assertTrue(permissions.contains("READ_PUBLIC_DATA"));
        
        // Should be sorted
        for (int i = 1; i < permissions.size(); i++) {
            assertTrue(permissions.get(i-1).compareTo(permissions.get(i)) <= 0);
        }
    }

    @Test
    void testFindRolesForOrganizationLevel() {
        // Test mid-level roles (levels 2-3)
        List<Role> midLevelRoles = roleRepository.findRolesForOrganizationLevel(2, 3);
        assertEquals(2, midLevelRoles.size()); // physicist, analyst
        
        // Test high-level roles (levels 3-4)
        List<Role> highLevelRoles = roleRepository.findRolesForOrganizationLevel(3, 4);
        assertEquals(2, highLevelRoles.size()); // admin, physicist
        
        // Test single level
        List<Role> singleLevel = roleRepository.findRolesForOrganizationLevel(1, 1);
        assertEquals(1, singleLevel.size()); // only guest
        assertEquals(guestRole.getId(), singleLevel.get(0).getId());
    }

    @Test
    void testRoleHierarchyOrdering() {
        List<Role> allRoles = roleRepository.findAllOrderedByHierarchy();
        
        // Verify hierarchy ordering
        for (int i = 1; i < allRoles.size(); i++) {
            assertTrue(allRoles.get(i-1).getHierarchyLevel() >= allRoles.get(i).getHierarchyLevel());
        }
    }

    @Test
    void testPermissionQueries() {
        // Test that admin has the most permissions
        Optional<Role> admin = roleRepository.findByName("ADMIN");
        assertTrue(admin.isPresent());
        assertTrue(admin.get().getPermissions().size() >= 4);
        
        // Test that guest has the fewest permissions
        Optional<Role> guest = roleRepository.findByName("GUEST");
        assertTrue(guest.isPresent());
        assertEquals(1, guest.get().getPermissions().size());
        assertTrue(guest.get().getPermissions().contains("READ_PUBLIC_DATA"));
    }

    @Test
    void testRoleCreationTimestamp() {
        // All roles should have creation timestamps
        List<Role> allRoles = roleRepository.findAll();
        for (Role role : allRoles) {
            assertNotNull(role.getCreatedAt());
            assertTrue(role.getCreatedAt().isBefore(LocalDateTime.now().plusSeconds(1)));
            assertTrue(role.getCreatedAt().isAfter(LocalDateTime.now().minusMinutes(1)));
        }
    }
}