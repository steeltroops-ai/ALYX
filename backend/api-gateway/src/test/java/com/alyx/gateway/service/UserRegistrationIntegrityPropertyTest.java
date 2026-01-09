package com.alyx.gateway.service;

import com.alyx.gateway.dto.UserRegistrationRequest;
import com.alyx.gateway.dto.UserRegistrationResponse;
import com.alyx.gateway.model.Role;
import com.alyx.gateway.model.User;
import com.alyx.gateway.model.UserRole;
import com.alyx.gateway.repository.RoleRepository;
import com.alyx.gateway.repository.UserRepository;
import net.jqwik.api.*;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for user registration integrity.
 * 
 * **Feature: neon-auth-system, Property 1: User Registration Integrity**
 * **Validates: Requirements 1.1, 1.5**
 * 
 * Tests that for any valid user registration data (unique email, strong password, valid names),
 * registering the user should create a database record with hashed password and return 
 * a success response with user ID.
 */
@Tag("Feature: neon-auth-system, Property 1: User Registration Integrity")
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserRegistrationIntegrityPropertyTest {

    private AuthService authService;
    
    @MockBean
    private UserRepository userRepository;
    
    @MockBean
    private RoleRepository roleRepository;
    
    @MockBean
    private PasswordService passwordService;
    
    @MockBean
    private SecurityAuditService auditService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, roleRepository, passwordService, auditService);
        
        // Setup default mock behaviors
        when(passwordService.validatePasswordStrength(anyString())).thenReturn();
        when(passwordService.hashPassword(anyString())).thenReturn("$2a$12$hashedPassword");
        doNothing().when(auditService).logSecurityEvent(anyString(), anyString(), anyString(), anyString());
    }

    /**
     * Property 1a: Valid registration creates database record
     * For any valid user registration data with unique email, the registration should succeed
     * and create a user record in the database with properly hashed password.
     */
    @Property(tries = 100)
    void validRegistrationCreatesRecord(@ForAll("validRegistrationRequests") UserRegistrationRequest request) {
        // Given: Email doesn't exist and role is available
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        
        Role mockRole = createMockRole(request.getRoleName());
        when(roleRepository.findByName(request.getRoleName())).thenReturn(Optional.of(mockRole));
        
        User savedUser = createMockUser(request, mockRole);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        
        // When: Registering the user
        UserRegistrationResponse response = authService.registerUser(request);
        
        // Then: Registration should succeed
        assertNotNull(response, "Registration response should not be null");
        assertNotNull(response.getUserId(), "User ID should be generated");
        assertEquals(request.getEmail(), response.getEmail(), "Email should match request");
        assertEquals(request.getFirstName(), response.getFirstName(), "First name should match request");
        assertEquals(request.getLastName(), response.getLastName(), "Last name should match request");
        assertEquals(request.getOrganization(), response.getOrganization(), "Organization should match request");
        assertEquals(request.getRoleName(), response.getRoleName(), "Role name should match request");
        assertEquals("User registered successfully", response.getMessage(), "Success message should be returned");
        
        // And: Password should be hashed, not stored in plaintext
        verify(passwordService).validatePasswordStrength(request.getPassword());
        verify(passwordService).hashPassword(request.getPassword());
        
        // And: User should be saved to database
        verify(userRepository).save(argThat(user -> 
            user.getEmail().equals(request.getEmail()) &&
            user.getFirstName().equals(request.getFirstName()) &&
            user.getLastName().equals(request.getLastName()) &&
            user.getOrganization().equals(request.getOrganization()) &&
            user.getRole().equals(mockRole) &&
            user.getPasswordHash().equals("$2a$12$hashedPassword")
        ));
        
        // And: Security event should be logged
        verify(auditService).logSecurityEvent(eq("REGISTRATION_SUCCESS"), eq("/api/auth/register"), 
            eq(savedUser.getId().toString()), contains("New user registered"));
    }

    /**
     * Property 1b: Registration with existing email fails
     * For any registration request with an email that already exists,
     * the registration should fail with appropriate error message.
     */
    @Property(tries = 50)
    void registrationWithExistingEmailFails(@ForAll("validRegistrationRequests") UserRegistrationRequest request) {
        // Given: Email already exists
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(true);
        
        // When/Then: Registration should fail
        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> authService.registerUser(request),
            "Registration with existing email should fail");
        
        assertEquals("User with this email already exists", exception.getMessage(),
            "Error message should indicate email already exists");
        
        // And: No user should be saved
        verify(userRepository, never()).save(any(User.class));
        
        // And: Security event should be logged
        verify(auditService).logSecurityEvent(eq("REGISTRATION_FAILED"), eq("/api/auth/register"), 
            eq(request.getEmail()), eq("Email already exists"));
    }

    /**
     * Property 1c: Registration with invalid password fails
     * For any registration request with invalid password,
     * the registration should fail with validation error.
     */
    @Property(tries = 50)
    void registrationWithInvalidPasswordFails(@ForAll("validRegistrationRequests") UserRegistrationRequest request) {
        // Given: Email doesn't exist but password is invalid
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        doThrow(new IllegalArgumentException("Password too weak"))
            .when(passwordService).validatePasswordStrength(request.getPassword());
        
        // When/Then: Registration should fail
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
            () -> authService.registerUser(request),
            "Registration with invalid password should fail");
        
        assertEquals("Password too weak", exception.getMessage(),
            "Error message should indicate password validation failure");
        
        // And: No user should be saved
        verify(userRepository, never()).save(any(User.class));
        
        // And: Security event should be logged
        verify(auditService).logSecurityEvent(eq("REGISTRATION_VALIDATION_FAILED"), eq("/api/auth/register"), 
            eq(request.getEmail()), contains("Validation error"));
    }

    /**
     * Property 1d: Registration assigns appropriate role
     * For any valid registration request, the user should be assigned
     * the appropriate role based on organization and request.
     */
    @Property(tries = 50)
    void registrationAssignsAppropriateRole(@ForAll("validRegistrationRequests") UserRegistrationRequest request) {
        // Given: Email doesn't exist and role determination logic
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        
        Role mockRole = createMockRole(determineExpectedRole(request.getRoleName(), request.getOrganization()));
        when(roleRepository.findByName(anyString())).thenReturn(Optional.of(mockRole));
        when(roleRepository.findDefaultRole()).thenReturn(Optional.of(mockRole));
        
        User savedUser = createMockUser(request, mockRole);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        
        // When: Registering the user
        UserRegistrationResponse response = authService.registerUser(request);
        
        // Then: User should be assigned appropriate role
        assertNotNull(response.getRoleName(), "Role should be assigned");
        
        // And: Role assignment should follow business logic
        String expectedRole = determineExpectedRole(request.getRoleName(), request.getOrganization());
        verify(roleRepository).findByName(expectedRole);
        
        // And: User should be saved with correct role
        verify(userRepository).save(argThat(user -> user.getRole().equals(mockRole)));
    }

    /**
     * Property 1e: Registration handles null/invalid input gracefully
     * For any registration request with null or invalid required fields,
     * the registration should fail with appropriate validation errors.
     */
    @Property(tries = 30)
    void registrationHandlesInvalidInputGracefully(@ForAll("invalidRegistrationRequests") UserRegistrationRequest request) {
        // When/Then: Registration should fail with validation error
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
            () -> authService.registerUser(request),
            "Registration with invalid input should fail");
        
        assertNotNull(exception.getMessage(), "Error message should be provided");
        assertTrue(exception.getMessage().contains("required") || 
                  exception.getMessage().contains("cannot be null") ||
                  exception.getMessage().contains("Invalid"),
            "Error message should indicate validation failure");
        
        // And: No user should be saved
        verify(userRepository, never()).save(any(User.class));
    }

    // Helper methods

    private Role createMockRole(String roleName) {
        Role role = new Role();
        role.setId(UUID.randomUUID());
        role.setName(roleName);
        role.setDescription("Test role");
        role.setPermissions(List.of("READ_DATA", "VIEW_RESULTS"));
        role.setHierarchyLevel(2);
        return role;
    }

    private User createMockUser(UserRegistrationRequest request, Role role) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(request.getEmail());
        user.setPasswordHash("$2a$12$hashedPassword");
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setOrganization(request.getOrganization());
        user.setRole(role);
        return user;
    }

    private String determineExpectedRole(String requestedRole, String organization) {
        // Simplified version of the role determination logic from AuthService
        if (organization != null) {
            switch (organization.toUpperCase()) {
                case "CERN", "FERMILAB", "DESY", "KEK" -> {
                    return "PHYSICIST";
                }
                case "ALYX_PHYSICS" -> {
                    return "ADMIN";
                }
                case "UNIVERSITY" -> {
                    return "ANALYST";
                }
            }
        }
        
        // For unknown organizations, limit to ANALYST unless requesting GUEST
        UserRole requested = UserRole.fromString(requestedRole);
        if (requested == UserRole.ADMIN || requested == UserRole.PHYSICIST) {
            return "ANALYST";
        }
        return requestedRole;
    }

    // Arbitraries for generating test data

    @Provide
    Arbitrary<UserRegistrationRequest> validRegistrationRequests() {
        return Combinators.combine(
            validEmails(),
            strongPasswords(),
            validNames(),
            validNames(),
            validOrganizations(),
            validRoles()
        ).as(UserRegistrationRequest::new);
    }

    @Provide
    Arbitrary<UserRegistrationRequest> invalidRegistrationRequests() {
        return Arbitraries.oneOf(
            // Null request
            Arbitraries.just((UserRegistrationRequest) null),
            // Missing email
            Combinators.combine(
                Arbitraries.just((String) null),
                strongPasswords(),
                validNames(),
                validNames(),
                validOrganizations(),
                validRoles()
            ).as(UserRegistrationRequest::new),
            // Missing password
            Combinators.combine(
                validEmails(),
                Arbitraries.just((String) null),
                validNames(),
                validNames(),
                validOrganizations(),
                validRoles()
            ).as(UserRegistrationRequest::new),
            // Missing first name
            Combinators.combine(
                validEmails(),
                strongPasswords(),
                Arbitraries.just((String) null),
                validNames(),
                validOrganizations(),
                validRoles()
            ).as(UserRegistrationRequest::new),
            // Missing last name
            Combinators.combine(
                validEmails(),
                strongPasswords(),
                validNames(),
                Arbitraries.just((String) null),
                validOrganizations(),
                validRoles()
            ).as(UserRegistrationRequest::new),
            // Invalid email format
            Combinators.combine(
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10), // no @ symbol
                strongPasswords(),
                validNames(),
                validNames(),
                validOrganizations(),
                validRoles()
            ).as(UserRegistrationRequest::new)
        );
    }

    @Provide
    Arbitrary<String> validEmails() {
        return Combinators.combine(
            Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10),
            Arbitraries.of("gmail.com", "physics.org", "cern.ch", "fermilab.gov", "university.edu")
        ).as((name, domain) -> name + "@" + domain);
    }

    @Provide
    Arbitrary<String> strongPasswords() {
        return Combinators.combine(
            Arbitraries.strings().withCharRange('A', 'Z').ofMinLength(1).ofMaxLength(3),
            Arbitraries.strings().withCharRange('a', 'z').ofMinLength(1).ofMaxLength(5),
            Arbitraries.strings().numeric().ofMinLength(1).ofMaxLength(3),
            Arbitraries.strings().withChars("!@#$%^&*").ofMinLength(1).ofMaxLength(2),
            Arbitraries.strings().alpha().ofMinLength(0).ofMaxLength(5)
        ).as((upper, lower, num, special, extra) -> upper + lower + num + special + extra);
    }

    @Provide
    Arbitrary<String> validNames() {
        return Arbitraries.strings().alpha().ofMinLength(2).ofMaxLength(20);
    }

    @Provide
    Arbitrary<String> validOrganizations() {
        return Arbitraries.of("CERN", "FERMILAB", "DESY", "KEK", "ALYX_PHYSICS", "UNIVERSITY", 
                             "MIT", "STANFORD", "UNKNOWN_ORG", null);
    }

    @Provide
    Arbitrary<String> validRoles() {
        return Arbitraries.of("ADMIN", "PHYSICIST", "ANALYST", "GUEST");
    }
}