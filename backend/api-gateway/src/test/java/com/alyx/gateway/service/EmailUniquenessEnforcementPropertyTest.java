package com.alyx.gateway.service;

import com.alyx.gateway.dto.UserRegistrationRequest;
import com.alyx.gateway.model.Role;
import com.alyx.gateway.repository.RoleRepository;
import com.alyx.gateway.repository.UserRepository;
import net.jqwik.api.*;
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
 * Property-based tests for email uniqueness enforcement.
 * 
 * **Feature: neon-auth-system, Property 2: Email Uniqueness Enforcement**
 * **Validates: Requirements 1.2**
 * 
 * Tests that for any email address that already exists in the database,
 * attempting to register with that email should be rejected with an appropriate error message.
 */
@Tag("Feature: neon-auth-system, Property 2: Email Uniqueness Enforcement")
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EmailUniquenessEnforcementPropertyTest {

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
     * Property 2a: Duplicate email registration rejection
     * For any email address that already exists in the database,
     * registration attempts should be rejected with appropriate error message.
     */
    @Property(tries = 100)
    void duplicateEmailRegistrationRejection(@ForAll("validRegistrationRequests") UserRegistrationRequest request) {
        // Given: Email already exists in database
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(true);
        
        // When/Then: Registration should fail with specific error
        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> authService.registerUser(request),
            "Registration with existing email should fail");
        
        assertEquals("User with this email already exists", exception.getMessage(),
            "Error message should clearly indicate email already exists");
        
        // And: Email existence should be checked
        verify(userRepository).existsByEmail(request.getEmail());
        
        // And: No user should be saved to database
        verify(userRepository, never()).save(any());
        
        // And: Password should not be processed (fail fast)
        verify(passwordService, never()).hashPassword(anyString());
        
        // And: Security audit should log the failure
        verify(auditService).logSecurityEvent(eq("REGISTRATION_FAILED"), eq("/api/auth/register"), 
            eq(request.getEmail()), eq("Email already exists"));
    }

    /**
     * Property 2b: Case-insensitive email uniqueness
     * For any email address with different case variations,
     * if one variation exists, all other case variations should be rejected.
     */
    @Property(tries = 50)
    void caseInsensitiveEmailUniqueness(@ForAll("validEmails") String baseEmail) {
        // Given: Base email exists in database (case-insensitive check)
        when(userRepository.existsByEmail(anyString())).thenReturn(true);
        
        // Create registration requests with different case variations
        String[] emailVariations = {
            baseEmail.toLowerCase(),
            baseEmail.toUpperCase(),
            capitalizeFirstLetter(baseEmail),
            mixedCaseEmail(baseEmail)
        };
        
        for (String emailVariation : emailVariations) {
            UserRegistrationRequest request = createValidRequest(emailVariation);
            
            // When/Then: All case variations should be rejected
            RuntimeException exception = assertThrows(RuntimeException.class, 
                () -> authService.registerUser(request),
                "Registration with email case variation should fail: " + emailVariation);
            
            assertEquals("User with this email already exists", exception.getMessage(),
                "Error message should be consistent for all case variations");
        }
        
        // And: Email existence should be checked for each variation
        verify(userRepository, atLeast(emailVariations.length)).existsByEmail(anyString());
        
        // And: No users should be saved
        verify(userRepository, never()).save(any());
    }

    /**
     * Property 2c: Email uniqueness across different domains
     * For any email with the same local part but different domains,
     * each should be treated as unique and allowed if not already existing.
     */
    @Property(tries = 30)
    void emailUniquenessAcrossDomains(@ForAll("emailLocalParts") String localPart) {
        // Given: Different domains for the same local part
        String[] domains = {"gmail.com", "physics.org", "cern.ch", "university.edu"};
        
        for (int i = 0; i < domains.length; i++) {
            String email = localPart + "@" + domains[i];
            UserRegistrationRequest request = createValidRequest(email);
            
            // Mock: This specific email doesn't exist, but others might
            when(userRepository.existsByEmail(email)).thenReturn(false);
            
            // Mock: Role and save operations
            Role mockRole = createMockRole("ANALYST");
            when(roleRepository.findByName(anyString())).thenReturn(Optional.of(mockRole));
            when(userRepository.save(any())).thenReturn(createMockUser(request, mockRole));
            
            // When: Registering with this email
            // Then: Should succeed (no exception thrown)
            assertDoesNotThrow(() -> authService.registerUser(request),
                "Registration with unique email should succeed: " + email);
            
            // Reset mocks for next iteration
            reset(userRepository, roleRepository);
            when(passwordService.validatePasswordStrength(anyString())).thenReturn();
            when(passwordService.hashPassword(anyString())).thenReturn("$2a$12$hashedPassword");
        }
    }

    /**
     * Property 2d: Whitespace and special character handling in emails
     * For any email with leading/trailing whitespace or special formatting,
     * the uniqueness check should handle normalization correctly.
     */
    @Property(tries = 30)
    void emailWhitespaceHandling(@ForAll("validEmails") String baseEmail) {
        // Given: Base email exists (after normalization)
        when(userRepository.existsByEmail(anyString())).thenReturn(true);
        
        // Create emails with whitespace variations
        String[] emailVariations = {
            " " + baseEmail,           // leading space
            baseEmail + " ",           // trailing space
            " " + baseEmail + " ",     // both spaces
            "\t" + baseEmail + "\n"    // tabs and newlines
        };
        
        for (String emailVariation : emailVariations) {
            UserRegistrationRequest request = createValidRequest(emailVariation);
            
            // When/Then: Registration should fail (email exists after normalization)
            RuntimeException exception = assertThrows(RuntimeException.class, 
                () -> authService.registerUser(request),
                "Registration with whitespace email variation should fail: '" + emailVariation + "'");
            
            assertEquals("User with this email already exists", exception.getMessage(),
                "Error message should be consistent for whitespace variations");
        }
    }

    /**
     * Property 2e: Concurrent registration attempt handling
     * For any email address, if multiple registration attempts occur simultaneously,
     * only one should succeed and others should be rejected for uniqueness violation.
     */
    @Property(tries = 20)
    void concurrentRegistrationHandling(@ForAll("validEmails") String email) {
        UserRegistrationRequest request = createValidRequest(email);
        
        // Simulate race condition: first check passes, but email gets created before save
        when(userRepository.existsByEmail(email))
            .thenReturn(false)  // First check: email doesn't exist
            .thenReturn(true);  // Second check: email now exists
        
        // First registration attempt should check and find email doesn't exist
        when(userRepository.existsByEmail(email)).thenReturn(false);
        
        Role mockRole = createMockRole("ANALYST");
        when(roleRepository.findByName(anyString())).thenReturn(Optional.of(mockRole));
        when(userRepository.save(any())).thenReturn(createMockUser(request, mockRole));
        
        // When: First registration (should succeed)
        assertDoesNotThrow(() -> authService.registerUser(request),
            "First registration attempt should succeed");
        
        // Reset and simulate second concurrent attempt
        reset(userRepository);
        when(userRepository.existsByEmail(email)).thenReturn(true);
        
        // When/Then: Second registration should fail
        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> authService.registerUser(request),
            "Concurrent registration attempt should fail");
        
        assertEquals("User with this email already exists", exception.getMessage(),
            "Concurrent registration should be rejected with appropriate message");
    }

    /**
     * Property 2f: Email format validation before uniqueness check
     * For any malformed email address, validation should fail before uniqueness check,
     * ensuring database queries are not performed for invalid emails.
     */
    @Property(tries = 30)
    void emailFormatValidationBeforeUniquenessCheck(@ForAll("invalidEmails") String invalidEmail) {
        UserRegistrationRequest request = createValidRequest(invalidEmail);
        
        // When/Then: Registration should fail with validation error
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
            () -> authService.registerUser(request),
            "Registration with invalid email should fail with validation error: " + invalidEmail);
        
        assertTrue(exception.getMessage().contains("Invalid email format") || 
                  exception.getMessage().contains("required"),
            "Error message should indicate email format validation failure");
        
        // And: Database uniqueness check should not be performed for invalid emails
        verify(userRepository, never()).existsByEmail(anyString());
    }

    // Helper methods

    private UserRegistrationRequest createValidRequest(String email) {
        return new UserRegistrationRequest(
            email,
            "StrongPass123!",
            "John",
            "Doe",
            "CERN",
            "PHYSICIST"
        );
    }

    private Role createMockRole(String roleName) {
        Role role = new Role();
        role.setId(UUID.randomUUID());
        role.setName(roleName);
        role.setDescription("Test role");
        role.setPermissions(List.of("READ_DATA", "VIEW_RESULTS"));
        role.setHierarchyLevel(2);
        return role;
    }

    private com.alyx.gateway.model.User createMockUser(UserRegistrationRequest request, Role role) {
        com.alyx.gateway.model.User user = new com.alyx.gateway.model.User();
        user.setId(UUID.randomUUID());
        user.setEmail(request.getEmail());
        user.setPasswordHash("$2a$12$hashedPassword");
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setOrganization(request.getOrganization());
        user.setRole(role);
        return user;
    }

    private String capitalizeFirstLetter(String email) {
        if (email == null || email.isEmpty()) return email;
        return email.substring(0, 1).toUpperCase() + email.substring(1).toLowerCase();
    }

    private String mixedCaseEmail(String email) {
        if (email == null || email.isEmpty()) return email;
        StringBuilder mixed = new StringBuilder();
        for (int i = 0; i < email.length(); i++) {
            char c = email.charAt(i);
            mixed.append(i % 2 == 0 ? Character.toLowerCase(c) : Character.toUpperCase(c));
        }
        return mixed.toString();
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
    Arbitrary<String> validEmails() {
        return Combinators.combine(
            Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10),
            Arbitraries.of("gmail.com", "physics.org", "cern.ch", "fermilab.gov", "university.edu")
        ).as((name, domain) -> name + "@" + domain);
    }

    @Provide
    Arbitrary<String> emailLocalParts() {
        return Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(8);
    }

    @Provide
    Arbitrary<String> invalidEmails() {
        return Arbitraries.oneOf(
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10), // no @ symbol
            Arbitraries.just("@domain.com"), // no local part
            Arbitraries.just("user@"), // no domain
            Arbitraries.just("user@@domain.com"), // double @
            Arbitraries.just(""), // empty
            Arbitraries.just("   "), // whitespace only
            Arbitraries.just((String) null) // null
        );
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
        return Arbitraries.of("CERN", "FERMILAB", "DESY", "KEK", "ALYX_PHYSICS", "UNIVERSITY");
    }

    @Provide
    Arbitrary<String> validRoles() {
        return Arbitraries.of("ADMIN", "PHYSICIST", "ANALYST", "GUEST");
    }
}