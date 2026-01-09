package com.alyx.gateway.service;

import com.alyx.gateway.model.Permission;
import com.alyx.gateway.model.Role;
import com.alyx.gateway.model.User;
import com.alyx.gateway.model.UserRole;
import com.alyx.gateway.repository.RoleRepository;
import com.alyx.gateway.repository.UserRepository;
import net.jqwik.api.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for permission validation accuracy
 * 
 * **Property 16: Permission Validation Accuracy**
 * **Validates: Requirements 8.2, 8.5**
 * 
 * Tests that permission validation correctly checks user permissions against
 * database-stored role definitions and enforces role-based access control consistently.
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("Feature: neon-auth-system, Property 16: Permission Validation Accuracy")
@Transactional
public class PermissionValidationAccuracyPropertyTest {
    
    @Autowired
    private RolePermissionService rolePermissionService;
    
    @Autowired
    private RoleRepository roleRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @BeforeEach
    void setUp() {
        // Ensure test roles exist in database
        createTestRolesIfNotExist();
    }
    
    /**
     * Property: For any user and permission, the permission check should accurately
     * reflect the user's role-based permissions as defined in the database
     */
    @Property(tries = 50)
    @Tag("Feature: neon-auth-system, Property 16: Permission Validation Accuracy")
    void permissionValidationShouldAccuratelyReflectRolePermissions(
            @ForAll("testUsers") User user,
            @ForAll("allPermissions") Permission permission) {
        
        // When checking if user has permission
        boolean hasPermission = rolePermissionService.hasPermission(user.getId(), permission);
        
        // Then the result should match the role's permission definition
        boolean expectedPermission = user.getRole().hasPermission(permission);
        
        assertThat(hasPermission).isEqualTo(expectedPermission);
    }
    
    /**
     * Property: For any user and permission name string, the permission check should
     * handle both valid and invalid permission names correctly
     */
    @Property(tries = 50)
    @Tag("Feature: neon-auth-system, Property 16: Permission Validation Accuracy")
    void permissionValidationShouldHandlePermissionNameStrings(
            @ForAll("testUsers") User user,
            @ForAll("permissionNames") String permissionName) {
        
        // When checking permission by name
        boolean hasPermission = rolePermissionService.hasPermission(user.getId(), permissionName);
        
        // Then the result should be consistent with enum-based check
        try {
            Permission permission = Permission.valueOf(permissionName.toUpperCase());
            boolean expectedPermission = user.getRole().hasPermission(permission);
            assertThat(hasPermission).isEqualTo(expectedPermission);
        } catch (IllegalArgumentException e) {
            // Invalid permission names should return false
            assertThat(hasPermission).isFalse();
        }
    }
    
    /**
     * Property: For any user and list of permissions, hasAnyPermission should return
     * true if the user has at least one of the permissions
     */
    @Property(tries = 50)
    @Tag("Feature: neon-auth-system, Property 16: Permission Validation Accuracy")
    void hasAnyPermissionShouldReturnTrueIfUserHasAtLeastOnePermission(
            @ForAll("testUsers") User user,
            @ForAll("permissionLists") List<Permission> permissions) {
        
        // When checking if user has any of the permissions
        boolean hasAnyPermission = rolePermissionService.hasAnyPermission(user.getId(), permissions);
        
        // Then the result should be true if user has at least one permission
        boolean expectedResult = permissions.stream()
            .anyMatch(permission -> user.getRole().hasPermission(permission));
        
        assertThat(hasAnyPermission).isEqualTo(expectedResult);
    }
    
    /**
     * Property: For any user and list of permissions, hasAllPermissions should return
     * true only if the user has all of the permissions
     */
    @Property(tries = 50)
    @Tag("Feature: neon-auth-system, Property 16: Permission Validation Accuracy")
    void hasAllPermissionsShouldReturnTrueOnlyIfUserHasAllPermissions(
            @ForAll("testUsers") User user,
            @ForAll("permissionLists") List<Permission> permissions) {
        
        // When checking if user has all permissions
        boolean hasAllPermissions = rolePermissionService.hasAllPermissions(user.getId(), permissions);
        
        // Then the result should be true only if user has all permissions
        boolean expectedResult = permissions.stream()
            .allMatch(permission -> user.getRole().hasPermission(permission));
        
        assertThat(hasAllPermissions).isEqualTo(expectedResult);
    }
    
    /**
     * Property: For any inactive or locked user, permission checks should return false
     * regardless of their role permissions
     */
    @Property(tries = 50)
    @Tag("Feature: neon-auth-system, Property 16: Permission Validation Accuracy")
    void inactiveOrLockedUsersShouldHaveNoPermissions(
            @ForAll("inactiveUsers") User inactiveUser,
            @ForAll("allPermissions") Permission permission) {
        
        // When checking permission for inactive/locked user
        boolean hasPermission = rolePermissionService.hasPermission(inactiveUser.getId(), permission);
        
        // Then the result should always be false
        assertThat(hasPermission).isFalse();
    }
    
    /**
     * Property: Permission validation should be consistent across multiple calls
     * with the same parameters (idempotent)
     */
    @Property(tries = 50)
    @Tag("Feature: neon-auth-system, Property 16: Permission Validation Accuracy")
    void permissionValidationShouldBeIdempotent(
            @ForAll("testUsers") User user,
            @ForAll("allPermissions") Permission permission) {
        
        // When checking permission multiple times
        boolean firstCheck = rolePermissionService.hasPermission(user.getId(), permission);
        boolean secondCheck = rolePermissionService.hasPermission(user.getId(), permission);
        boolean thirdCheck = rolePermissionService.hasPermission(user.getId(), permission);
        
        // Then all results should be identical
        assertThat(firstCheck).isEqualTo(secondCheck);
        assertThat(secondCheck).isEqualTo(thirdCheck);
    }
    
    /**
     * Property: For any role name, getting permissions for that role should return
     * the exact permissions defined in the database
     */
    @Property(tries = 50)
    @Tag("Feature: neon-auth-system, Property 16: Permission Validation Accuracy")
    void getRolePermissionsShouldReturnExactDatabasePermissions(
            @ForAll("validRoleNames") String roleName) {
        
        // When getting permissions for a role
        List<String> rolePermissions = rolePermissionService.getPermissionsForRole(roleName);
        
        // Then the result should match the database definition
        Optional<Role> roleOpt = roleRepository.findByName(roleName);
        if (roleOpt.isPresent()) {
            Role role = roleOpt.get();
            List<String> expectedPermissions = role.getPermissions() != null ? 
                role.getPermissions() : List.of();
            assertThat(rolePermissions).containsExactlyInAnyOrderElementsOf(expectedPermissions);
        } else {
            assertThat(rolePermissions).isEmpty();
        }
    }
    
    /**
     * Property: For any role and permission, roleHasPermission should accurately
     * reflect the role's permission definition
     */
    @Property(tries = 50)
    @Tag("Feature: neon-auth-system, Property 16: Permission Validation Accuracy")
    void roleHasPermissionShouldAccuratelyReflectRoleDefinition(
            @ForAll("validRoleNames") String roleName,
            @ForAll("allPermissions") Permission permission) {
        
        // When checking if role has permission
        boolean roleHasPermission = rolePermissionService.roleHasPermission(roleName, permission);
        
        // Then the result should match the role's definition
        Optional<Role> roleOpt = roleRepository.findByName(roleName);
        if (roleOpt.isPresent()) {
            boolean expectedResult = roleOpt.get().hasPermission(permission);
            assertThat(roleHasPermission).isEqualTo(expectedResult);
        } else {
            assertThat(roleHasPermission).isFalse();
        }
    }
    
    // Arbitraries for test data generation
    
    @Provide
    Arbitrary<User> testUsers() {
        return Combinators.combine(
            Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20),
            Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20),
            validOrganizations(),
            validRoleNames()
        ).as((firstName, lastName, organization, roleName) -> {
            String email = firstName.toLowerCase() + "." + lastName.toLowerCase() + "@" + organization.toLowerCase() + ".org";
            Role role = roleRepository.findByName(roleName).orElseThrow();
            User user = new User(email, "hashedPassword", firstName, lastName, organization, role);
            user.setIsActive(true);
            user.setEmailVerified(true);
            return userRepository.save(user);
        });
    }
    
    @Provide
    Arbitrary<User> inactiveUsers() {
        return Combinators.combine(
            Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20),
            Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20),
            validOrganizations(),
            validRoleNames(),
            Arbitraries.of(true, false) // isActive
        ).as((firstName, lastName, organization, roleName, isActive) -> {
            String email = "inactive." + firstName.toLowerCase() + "@" + organization.toLowerCase() + ".org";
            Role role = roleRepository.findByName(roleName).orElseThrow();
            User user = new User(email, "hashedPassword", firstName, lastName, organization, role);
            user.setIsActive(isActive);
            if (isActive) {
                // If active, make it locked instead
                user.lockAccount(15);
            }
            return userRepository.save(user);
        });
    }
    
    @Provide
    Arbitrary<String> validOrganizations() {
        return Arbitraries.of("CERN", "FERMILAB", "DESY", "KEK", "ALYX_PHYSICS", "UNIVERSITY");
    }
    
    @Provide
    Arbitrary<String> validRoleNames() {
        return Arbitraries.of("ADMIN", "PHYSICIST", "ANALYST", "GUEST");
    }
    
    @Provide
    Arbitrary<Permission> allPermissions() {
        return Arbitraries.of(Permission.values());
    }
    
    @Provide
    Arbitrary<String> permissionNames() {
        // Mix of valid and invalid permission names
        List<String> validNames = Arrays.stream(Permission.values())
            .map(Permission::name)
            .toList();
        List<String> invalidNames = List.of("INVALID_PERMISSION", "FAKE_PERM", "NOT_A_PERMISSION");
        
        return Arbitraries.oneOf(
            Arbitraries.of(validNames.toArray(new String[0])),
            Arbitraries.of(invalidNames.toArray(new String[0]))
        );
    }
    
    @Provide
    Arbitrary<List<Permission>> permissionLists() {
        return Arbitraries.of(Permission.values())
            .list()
            .ofMinSize(1)
            .ofMaxSize(5);
    }
    
    // Helper methods
    
    private void createTestRolesIfNotExist() {
        for (UserRole userRole : UserRole.values()) {
            if (roleRepository.findByName(userRole.getRoleName()).isEmpty()) {
                Role role = Role.fromUserRole(userRole);
                roleRepository.save(role);
            }
        }
    }
}