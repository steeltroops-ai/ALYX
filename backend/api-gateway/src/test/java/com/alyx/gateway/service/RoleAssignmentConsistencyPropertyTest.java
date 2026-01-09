package com.alyx.gateway.service;

import com.alyx.gateway.model.Role;
import com.alyx.gateway.model.User;
import com.alyx.gateway.model.UserRole;
import com.alyx.gateway.repository.RoleRepository;
import com.alyx.gateway.repository.UserRepository;
import net.jqwik.api.*;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.Size;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for role assignment consistency
 * 
 * **Property 15: Role Assignment Consistency**
 * **Validates: Requirements 8.1**
 * 
 * Tests that role assignment follows consistent business rules based on
 * organization and user hierarchy, ensuring proper access control.
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("Feature: neon-auth-system, Property 15: Role Assignment Consistency")
@Transactional
public class RoleAssignmentConsistencyPropertyTest {
    
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
     * Property: For any valid organization and role combination, role assignment validation
     * should be consistent with organization-based rules and hierarchy constraints
     */
    @Property(tries = 50) // Reduced tries for faster execution
    @Tag("Feature: neon-auth-system, Property 15: Role Assignment Consistency")
    void roleAssignmentShouldBeConsistentWithOrganizationRules(
            @ForAll("validOrganizations") String organization,
            @ForAll("validRoleNames") String roleName) {
        
        // When validating role assignment
        boolean isValid = rolePermissionService.validateRoleAssignment(roleName, organization, null);
        
        // Then the validation should be consistent with organization rules
        boolean expectedValid = isRoleAllowedForOrganization(roleName, organization);
        
        assertThat(isValid).isEqualTo(expectedValid);
        
        // And if valid, the role should exist in the database
        if (isValid) {
            Optional<Role> role = roleRepository.findByName(roleName);
            assertThat(role).isPresent();
        }
    }
    
    /**
     * Property: For any unknown organization, only ANALYST and GUEST roles should be assignable
     */
    @Property(tries = 50)
    @Tag("Feature: neon-auth-system, Property 15: Role Assignment Consistency")
    void unknownOrganizationShouldRestrictToLowerPrivilegeRoles(
            @ForAll("unknownOrganizations") String unknownOrganization,
            @ForAll("validRoleNames") String roleName) {
        
        // When validating role assignment for unknown organization
        boolean isValid = rolePermissionService.validateRoleAssignment(roleName, unknownOrganization, null);
        
        // Then only ANALYST and GUEST roles should be valid
        UserRole userRole = UserRole.fromString(roleName);
        boolean shouldBeValid = (userRole == UserRole.ANALYST || userRole == UserRole.GUEST);
        
        assertThat(isValid).isEqualTo(shouldBeValid);
    }
    
    /**
     * Property: Role assignment validation should be idempotent - multiple calls with
     * same parameters should return same result
     */
    @Property(tries = 50)
    @Tag("Feature: neon-auth-system, Property 15: Role Assignment Consistency")
    void roleAssignmentValidationShouldBeIdempotent(
            @ForAll("validOrganizations") String organization,
            @ForAll("validRoleNames") String roleName) {
        
        // When validating role assignment multiple times
        boolean firstResult = rolePermissionService.validateRoleAssignment(roleName, organization, null);
        boolean secondResult = rolePermissionService.validateRoleAssignment(roleName, organization, null);
        boolean thirdResult = rolePermissionService.validateRoleAssignment(roleName, organization, null);
        
        // Then all results should be identical
        assertThat(firstResult).isEqualTo(secondResult);
        assertThat(secondResult).isEqualTo(thirdResult);
    }
    
    // Arbitraries for test data generation
    
    @Provide
    Arbitrary<String> validOrganizations() {
        return Arbitraries.of("CERN", "FERMILAB", "DESY", "KEK", "ALYX_PHYSICS", "UNIVERSITY");
    }
    
    @Provide
    Arbitrary<String> unknownOrganizations() {
        return Arbitraries.of("UNKNOWN_ORG", "RANDOM_UNIVERSITY", "PRIVATE_COMPANY", "STARTUP");
    }
    
    @Provide
    Arbitrary<String> validRoleNames() {
        return Arbitraries.of("ADMIN", "PHYSICIST", "ANALYST", "GUEST");
    }
    
    // Helper methods
    
    private boolean isRoleAllowedForOrganization(String roleName, String organization) {
        if (organization == null) {
            UserRole userRole = UserRole.fromString(roleName);
            return userRole == UserRole.ANALYST || userRole == UserRole.GUEST;
        }
        
        return switch (organization.toUpperCase()) {
            case "CERN", "FERMILAB", "DESY", "KEK" -> 
                List.of("PHYSICIST", "ANALYST", "GUEST").contains(roleName.toUpperCase());
            case "ALYX_PHYSICS" -> 
                List.of("ADMIN", "PHYSICIST", "ANALYST", "GUEST").contains(roleName.toUpperCase());
            case "UNIVERSITY" -> 
                List.of("ANALYST", "GUEST").contains(roleName.toUpperCase());
            default -> {
                UserRole userRole = UserRole.fromString(roleName);
                yield userRole == UserRole.ANALYST || userRole == UserRole.GUEST;
            }
        };
    }
    
    private void createTestRolesIfNotExist() {
        for (UserRole userRole : UserRole.values()) {
            if (roleRepository.findByName(userRole.getRoleName()).isEmpty()) {
                Role role = Role.fromUserRole(userRole);
                roleRepository.save(role);
            }
        }
    }
}