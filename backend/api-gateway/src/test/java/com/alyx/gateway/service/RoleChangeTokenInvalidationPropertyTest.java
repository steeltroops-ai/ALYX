package com.alyx.gateway.service;

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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for role change token invalidation
 * 
 * **Property 17: Role Change Token Invalidation**
 * **Validates: Requirements 8.4**
 * 
 * Tests that when a user's role changes, their existing tokens are invalidated
 * and require new token generation with updated permissions.
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("Feature: neon-auth-system, Property 17: Role Change Token Invalidation")
@Transactional
public class RoleChangeTokenInvalidationPropertyTest {
    
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
     * Property: For any successful role change, the user's tokens should be invalidated
     */
    @Property(tries = 50)
    @Tag("Feature: neon-auth-system, Property 17: Role Change Token Invalidation")
    void successfulRoleChangeShouldInvalidateUserTokens(
            @ForAll("testUsers") User targetUser,
            @ForAll("validRoleNames") String newRoleName,
            @ForAll("adminUsers") User adminUser) {
        
        // Given a user with a current role
        String originalRoleName = targetUser.getRole().getName();
        
        // Skip if trying to assign the same role
        if (originalRoleName.equals(newRoleName)) {
            return;
        }
        
        // Ensure the token is not already invalidated
        boolean wasInvalidatedBefore = rolePermissionService.isTokenInvalidated(targetUser.getId());
        
        // When the role is successfully changed
        boolean roleChangeSucceeded = rolePermissionService.changeUserRole(
            targetUser.getId(), newRoleName, adminUser.getId());
        
        if (roleChangeSucceeded) {
            // Then the user's tokens should be invalidated
            boolean isInvalidatedAfter = rolePermissionService.isTokenInvalidated(targetUser.getId());
            assertThat(isInvalidatedAfter).isTrue();
            
            // Cleanup: restore original role
            rolePermissionService.changeUserRole(targetUser.getId(), originalRoleName, adminUser.getId());
        }
    }
    
    /**
     * Property: Token invalidation should be idempotent - multiple invalidations
     * should have the same effect as a single invalidation
     */
    @Property(tries = 50)
    @Tag("Feature: neon-auth-system, Property 17: Role Change Token Invalidation")
    void tokenInvalidationShouldBeIdempotent(@ForAll("testUsers") User user) {
        
        // When invalidating tokens multiple times
        rolePermissionService.invalidateUserTokens(user.getId());
        boolean firstCheck = rolePermissionService.isTokenInvalidated(user.getId());
        
        rolePermissionService.invalidateUserTokens(user.getId());
        boolean secondCheck = rolePermissionService.isTokenInvalidated(user.getId());
        
        rolePermissionService.invalidateUserTokens(user.getId());
        boolean thirdCheck = rolePermissionService.isTokenInvalidated(user.getId());
        
        // Then all checks should return true and be consistent
        assertThat(firstCheck).isTrue();
        assertThat(secondCheck).isTrue();
        assertThat(thirdCheck).isTrue();
        assertThat(firstCheck).isEqualTo(secondCheck);
        assertThat(secondCheck).isEqualTo(thirdCheck);
    }
    
    /**
     * Property: For any user ID, token invalidation check should be consistent
     * across multiple calls (idempotent read operation)
     */
    @Property(tries = 50)
    @Tag("Feature: neon-auth-system, Property 17: Role Change Token Invalidation")
    void tokenInvalidationCheckShouldBeIdempotent(@ForAll("testUsers") User user) {
        
        // Given a user with potentially invalidated tokens
        boolean initialState = rolePermissionService.isTokenInvalidated(user.getId());
        
        // When checking invalidation status multiple times
        boolean firstCheck = rolePermissionService.isTokenInvalidated(user.getId());
        boolean secondCheck = rolePermissionService.isTokenInvalidated(user.getId());
        boolean thirdCheck = rolePermissionService.isTokenInvalidated(user.getId());
        
        // Then all checks should return the same result
        assertThat(firstCheck).isEqualTo(initialState);
        assertThat(secondCheck).isEqualTo(initialState);
        assertThat(thirdCheck).isEqualTo(initialState);
        assertThat(firstCheck).isEqualTo(secondCheck);
        assertThat(secondCheck).isEqualTo(thirdCheck);
    }
    
    /**
     * Property: Role change should only succeed when validation passes, and
     * tokens should only be invalidated on successful role changes
     */
    @Property(tries = 50)
    @Tag("Feature: neon-auth-system, Property 17: Role Change Token Invalidation")
    void tokenInvalidationShouldOnlyOccurOnSuccessfulRoleChange(
            @ForAll("testUsers") User targetUser,
            @ForAll("validRoleNames") String newRoleName,
            @ForAll("testUsers") User changerUser) {
        
        // Given the current invalidation state
        boolean wasInvalidatedBefore = rolePermissionService.isTokenInvalidated(targetUser.getId());
        
        // When attempting to change role (may or may not succeed based on permissions)
        boolean roleChangeSucceeded = rolePermissionService.changeUserRole(
            targetUser.getId(), newRoleName, changerUser.getId());
        
        // Then tokens should only be invalidated if role change succeeded
        boolean isInvalidatedAfter = rolePermissionService.isTokenInvalidated(targetUser.getId());
        
        if (roleChangeSucceeded) {
            // If role change succeeded, tokens should be invalidated
            assertThat(isInvalidatedAfter).isTrue();
        } else {
            // If role change failed, invalidation state should be unchanged
            assertThat(isInvalidatedAfter).isEqualTo(wasInvalidatedBefore);
        }
    }
    
    /**
     * Property: For any null or invalid user ID, token invalidation operations
     * should handle gracefully without throwing exceptions
     */
    @Property(tries = 50)
    @Tag("Feature: neon-auth-system, Property 17: Role Change Token Invalidation")
    void tokenInvalidationShouldHandleInvalidUserIds(@ForAll("invalidUserIds") UUID invalidUserId) {
        
        // When performing token operations with invalid user ID
        // Then operations should not throw exceptions
        try {
            rolePermissionService.invalidateUserTokens(invalidUserId);
            boolean isInvalidated = rolePermissionService.isTokenInvalidated(invalidUserId);
            
            // Invalid user IDs should be treated as invalidated for security
            if (invalidUserId == null) {
                assertThat(isInvalidated).isTrue();
            }
        } catch (Exception e) {
            // Should not throw exceptions for invalid user IDs
            assertThat(e).isNull();
        }
    }
    
    /**
     * Property: Role change with validation should be consistent - if validation
     * passes, the actual role change should succeed
     */
    @Property(tries = 30) // Fewer tries due to database operations
    @Tag("Feature: neon-auth-system, Property 17: Role Change Token Invalidation")
    void roleChangeConsistencyWithValidation(
            @ForAll("testUsers") User targetUser,
            @ForAll("validRoleNames") String newRoleName,
            @ForAll("adminUsers") User adminUser) {
        
        // Given role assignment validation
        boolean validationPassed = rolePermissionService.validateRoleAssignment(
            newRoleName, targetUser.getOrganization(), adminUser.getId());
        
        // When attempting role change
        boolean roleChangeSucceeded = rolePermissionService.changeUserRole(
            targetUser.getId(), newRoleName, adminUser.getId());
        
        // Then if validation passed, role change should succeed and tokens invalidated
        if (validationPassed && !targetUser.getRole().getName().equals(newRoleName)) {
            assertThat(roleChangeSucceeded).isTrue();
            assertThat(rolePermissionService.isTokenInvalidated(targetUser.getId())).isTrue();
            
            // Verify role was actually changed in database
            Optional<User> updatedUser = userRepository.findById(targetUser.getId());
            assertThat(updatedUser).isPresent();
            assertThat(updatedUser.get().getRole().getName()).isEqualTo(newRoleName);
            
            // Cleanup: restore original role
            rolePermissionService.changeUserRole(
                targetUser.getId(), targetUser.getRole().getName(), adminUser.getId());
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
    Arbitrary<User> adminUsers() {
        return Combinators.combine(
            Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20),
            Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20),
            Arbitraries.of("ALYX_PHYSICS", "CERN")
        ).as((firstName, lastName, organization) -> {
            String email = "admin." + firstName.toLowerCase() + "@" + organization.toLowerCase() + ".org";
            Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();
            User user = new User(email, "hashedPassword", firstName, lastName, organization, adminRole);
            user.setIsActive(true);
            user.setEmailVerified(true);
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
    Arbitrary<UUID> invalidUserIds() {
        return Arbitraries.oneOf(
            Arbitraries.just((UUID) null),
            Arbitraries.create(() -> UUID.randomUUID()) // Random UUIDs that don't exist in DB
        );
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