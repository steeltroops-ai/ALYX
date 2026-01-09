package com.alyx.gateway.service;

import com.alyx.gateway.dto.UserRegistrationRequest;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for input validation consistency.
 * 
 * **Feature: neon-auth-system, Property 3: Input Validation Consistency**
 * **Validates: Requirements 1.3, 1.4**
 * 
 * Tests that for any invalid input data (malformed emails, weak passwords),
 * the registration and login endpoints should reject the request with specific validation errors.
 */
@Tag("Feature: neon-auth-system, Property 3: Input Validation Consistency")
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InputValidationConsistencyPropertyTest {

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
        doNothing().when(auditService).logSecurityEvent(anyString(), anyString(), anyString(), anyString());
    }

    /**
     * Property 3a: Invalid email format rejection
     * For any malformed email address, the registration should be rejected
     * with specific email format validation errors.
     */
    @Property(tries = 100)
    void invalidEmailFormatRejection(@ForAll("invalidEmails") String invalidEmail) {
        UserRegistrationRequest request = new UserRegistrationRequest(
            invalidEmail,
            "ValidPass123!",
            "John",
            "Doe",
            "CERN",
            "PHYSICIST"
        );
        
        // When/Then: Registration should fail with validation error
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
            () -> authService.registerUser(request),
            "Registration with invalid email should fail: " + invalidEmail);
        
        assertTrue(exception.getMessage().contains("Invalid email format") || 
                  exception.getMessage().contains("Email is required") ||
                  exception.getMessage().contains("cannot be null"),
            "Error message should indicate email validation failure: " + exception.getMessage());
        
        // And: No database operations should be performed
        verify(userRepository, never()).existsByEmail(anyString());
        verify(userRepository, never()).save(any());
        
        // And: Security audit should log validation failure
        verify(auditService).logSecurityEvent(eq("REGISTRATION_VALIDATION_FAILED"), eq("/api/auth/register"), 
            anyString(), contains("Validation error"));
    }

    /**
     * Property 3b: Weak password rejection
     * For any password that doesn't meet strength requirements,
     * the registration should be rejected with specific password validation errors.
     */
    @Property(tries = 100)
    void weakPasswordRejection(@ForAll("weakPasswords") String weakPassword) {
        UserRegistrationRequest request = new UserRegistrationRequest(
            "test@physics.org",
            weakPassword,
            "John",
            "Doe",
            "CERN",
            "PHYSICIST"
        );
        
        // Given: Email doesn't exist but password is weak
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        doThrow(new IllegalArgumentException("Password does not meet requirements"))
            .when(passwordService).validatePasswordStrength(weakPassword);
        
        // When/Then: Registration should fail with password validation error
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
            () -> authService.registerUser(request),
            "Registration with weak password should fail: " + weakPassword);
        
        assertTrue(exception.getMessage().contains("Password") || 
                  exception.getMessage().contains("required") ||
                  exception.getMessage().contains("requirements"),
            "Error message should indicate password validation failure: " + exception.getMessage());
        
        // And: Password validation should be attempted
        verify(passwordService).validatePasswordStrength(weakPassword);
        
        // And: No user should be saved
        verify(userRepository, never()).save(any());
        
        // And: Security audit should log validation failure
        verify(auditService).logSecurityEvent(eq("REGISTRATION_VALIDATION_FAILED"), eq("/api/auth/register"), 
            anyString(), contains("Validation error"));
    }

    /**
     * Property 3c: Missing required fields rejection
     * For any registration request with missing required fields,
     * the registration should be rejected with specific field validation errors.
     */
    @Property(tries = 50)
    void missingRequiredFieldsRejection(@ForAll("requestsWithMissingFields") UserRegistrationRequest request) {
        // When/Then: Registration should fail with validation error
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
            () -> authService.registerUser(request),
            "Registration with missing fields should fail");
        
        assertTrue(exception.getMessage().contains("required") || 
                  exception.getMessage().contains("cannot be null") ||
                  exception.getMessage().contains("cannot be empty"),
            "Error message should indicate missing field validation failure: " + exception.getMessage());
        
        // And: No database operations should be performed
        verify(userRepository, never()).existsByEmail(anyString());
        verify(userRepository, never()).save(any());
        
        // And: Security audit should log validation failure
        verify(auditService).logSecurityEvent(eq("REGISTRATION_VALIDATION_FAILED"), eq("/api/auth/register"), 
            anyString(), contains("Validation error"));
    }

    /**
     * Property 3d: Field length validation
     * For any registration request with fields exceeding maximum length,
     * the registration should be rejected with appropriate length validation errors.
     */
    @Property(tries = 50)
    void fieldLengthValidation(@ForAll("requestsWithOversizedFields") UserRegistrationRequest request) {
        // Given: Email doesn't exist
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        
        // When/Then: Registration should fail with validation error
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
            () -> authService.registerUser(request),
            "Registration with oversized fields should fail");
        
        // Note: The specific validation might happen at different levels (DTO validation, service validation)
        // We accept various validation error messages that indicate length issues
        String message = exception.getMessage().toLowerCase();
        assertTrue(message.contains("exceed") || 
                  message.contains("too long") ||
                  message.contains("maximum") ||
                  message.contains("length") ||
                  message.contains("characters"),
            "Error message should indicate field length validation failure: " + exception.getMessage());
    }

    /**
     * Property 3e: Null request handling
     * For any null registration request, the service should handle gracefully
     * with appropriate error message without causing system crashes.
     */
    @Property(tries = 10)
    void nullRequestHandling() {
        // When/Then: Registration with null request should fail gracefully
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
            () -> authService.registerUser(null),
            "Registration with null request should fail");
        
        assertEquals("Registration request cannot be null", exception.getMessage(),
            "Error message should indicate null request");
        
        // And: No database operations should be performed
        verify(userRepository, never()).existsByEmail(anyString());
        verify(userRepository, never()).save(any());
        
        // And: Security audit should log validation failure
        verify(auditService).logSecurityEvent(eq("REGISTRATION_VALIDATION_FAILED"), eq("/api/auth/register"), 
            anyString(), contains("Validation error"));
    }

    /**
     * Property 3f: Whitespace-only field validation
     * For any registration request with whitespace-only required fields,
     * the registration should be rejected as if the fields were empty.
     */
    @Property(tries = 30)
    void whitespaceOnlyFieldValidation(@ForAll("requestsWithWhitespaceFields") UserRegistrationRequest request) {
        // When/Then: Registration should fail with validation error
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
            () -> authService.registerUser(request),
            "Registration with whitespace-only fields should fail");
        
        assertTrue(exception.getMessage().contains("required") || 
                  exception.getMessage().contains("cannot be null") ||
                  exception.getMessage().contains("Invalid"),
            "Error message should indicate field validation failure: " + exception.getMessage());
        
        // And: No database operations should be performed
        verify(userRepository, never()).existsByEmail(anyString());
        verify(userRepository, never()).save(any());
    }

    /**
     * Property 3g: Special character handling in names
     * For any registration request with special characters in name fields,
     * the validation should handle them appropriately (either accept or reject consistently).
     */
    @Property(tries = 30)
    void specialCharacterHandlingInNames(@ForAll("namesWithSpecialChars") String firstName,
                                        @ForAll("namesWithSpecialChars") String lastName) {
        UserRegistrationRequest request = new UserRegistrationRequest(
            "test@physics.org",
            "ValidPass123!",
            firstName,
            lastName,
            "CERN",
            "PHYSICIST"
        );
        
        // Given: Email doesn't exist and password is valid
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordService.validatePasswordStrength(anyString())).thenReturn();
        
        try {
            // When: Attempting registration
            authService.registerUser(request);
            
            // Then: If it succeeds, names should be properly handled
            // (This tests that special characters don't cause system errors)
            verify(userRepository).existsByEmail(request.getEmail());
            
        } catch (IllegalArgumentException e) {
            // Then: If it fails, should be due to validation, not system error
            assertTrue(e.getMessage().contains("name") || 
                      e.getMessage().contains("character") ||
                      e.getMessage().contains("invalid") ||
                      e.getMessage().contains("required"),
                "Validation error should be related to name validation: " + e.getMessage());
        } catch (Exception e) {
            fail("Special characters in names should not cause system errors: " + e.getMessage());
        }
    }

    /**
     * Property 3h: Role validation
     * For any registration request with invalid or non-existent role,
     * the registration should be handled appropriately (either assign default or reject).
     */
    @Property(tries = 30)
    void roleValidation(@ForAll("invalidRoles") String invalidRole) {
        UserRegistrationRequest request = new UserRegistrationRequest(
            "test@physics.org",
            "ValidPass123!",
            "John",
            "Doe",
            "CERN",
            invalidRole
        );
        
        // Given: Email doesn't exist, password is valid, but role is invalid
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordService.validatePasswordStrength(anyString())).thenReturn();
        when(roleRepository.findByName(anyString())).thenReturn(java.util.Optional.empty());
        when(roleRepository.findDefaultRole()).thenReturn(java.util.Optional.empty());
        
        // When/Then: Registration should fail due to role issues
        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> authService.registerUser(request),
            "Registration with invalid role should fail: " + invalidRole);
        
        assertTrue(exception.getMessage().contains("role") || 
                  exception.getMessage().contains("assign") ||
                  exception.getMessage().contains("Unable"),
            "Error message should indicate role assignment failure: " + exception.getMessage());
        
        // And: Role lookup should be attempted
        verify(roleRepository).findByName(anyString());
    }

    // Arbitraries for generating test data

    @Provide
    Arbitrary<String> invalidEmails() {
        return Arbitraries.oneOf(
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10), // no @ symbol
            Arbitraries.just("@domain.com"), // no local part
            Arbitraries.just("user@"), // no domain
            Arbitraries.just("user@@domain.com"), // double @
            Arbitraries.just("user@.com"), // invalid domain
            Arbitraries.just("user@domain."), // trailing dot
            Arbitraries.just(""), // empty
            Arbitraries.just("   "), // whitespace only
            Arbitraries.just((String) null) // null
        );
    }

    @Provide
    Arbitrary<String> weakPasswords() {
        return Arbitraries.oneOf(
            // Too short
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(7),
            // No uppercase
            Arbitraries.strings().withCharRange('a', 'z').ofMinLength(8).ofMaxLength(15),
            // No lowercase
            Arbitraries.strings().withCharRange('A', 'Z').ofMinLength(8).ofMaxLength(15),
            // No numbers
            Arbitraries.strings().alpha().ofMinLength(8).ofMaxLength(15),
            // No special characters
            Arbitraries.strings().alpha().numeric().ofMinLength(8).ofMaxLength(15),
            // Empty or null
            Arbitraries.just(""),
            Arbitraries.just("   "),
            Arbitraries.just((String) null)
        );
    }

    @Provide
    Arbitrary<UserRegistrationRequest> requestsWithMissingFields() {
        return Arbitraries.oneOf(
            // Missing email
            Arbitraries.just(new UserRegistrationRequest(null, "ValidPass123!", "John", "Doe", "CERN", "PHYSICIST")),
            Arbitraries.just(new UserRegistrationRequest("", "ValidPass123!", "John", "Doe", "CERN", "PHYSICIST")),
            // Missing password
            Arbitraries.just(new UserRegistrationRequest("test@physics.org", null, "John", "Doe", "CERN", "PHYSICIST")),
            Arbitraries.just(new UserRegistrationRequest("test@physics.org", "", "John", "Doe", "CERN", "PHYSICIST")),
            // Missing first name
            Arbitraries.just(new UserRegistrationRequest("test@physics.org", "ValidPass123!", null, "Doe", "CERN", "PHYSICIST")),
            Arbitraries.just(new UserRegistrationRequest("test@physics.org", "ValidPass123!", "", "Doe", "CERN", "PHYSICIST")),
            // Missing last name
            Arbitraries.just(new UserRegistrationRequest("test@physics.org", "ValidPass123!", "John", null, "CERN", "PHYSICIST")),
            Arbitraries.just(new UserRegistrationRequest("test@physics.org", "ValidPass123!", "John", "", "CERN", "PHYSICIST")),
            // Missing role
            Arbitraries.just(new UserRegistrationRequest("test@physics.org", "ValidPass123!", "John", "Doe", "CERN", null)),
            Arbitraries.just(new UserRegistrationRequest("test@physics.org", "ValidPass123!", "John", "Doe", "CERN", ""))
        );
    }

    @Provide
    Arbitrary<UserRegistrationRequest> requestsWithOversizedFields() {
        return Arbitraries.oneOf(
            // Oversized first name (>100 chars)
            Combinators.combine(
                Arbitraries.just("test@physics.org"),
                Arbitraries.just("ValidPass123!"),
                Arbitraries.strings().alpha().ofMinLength(101).ofMaxLength(200),
                Arbitraries.just("Doe"),
                Arbitraries.just("CERN"),
                Arbitraries.just("PHYSICIST")
            ).as(UserRegistrationRequest::new),
            // Oversized last name (>100 chars)
            Combinators.combine(
                Arbitraries.just("test@physics.org"),
                Arbitraries.just("ValidPass123!"),
                Arbitraries.just("John"),
                Arbitraries.strings().alpha().ofMinLength(101).ofMaxLength(200),
                Arbitraries.just("CERN"),
                Arbitraries.just("PHYSICIST")
            ).as(UserRegistrationRequest::new),
            // Oversized organization (>100 chars)
            Combinators.combine(
                Arbitraries.just("test@physics.org"),
                Arbitraries.just("ValidPass123!"),
                Arbitraries.just("John"),
                Arbitraries.just("Doe"),
                Arbitraries.strings().alpha().ofMinLength(101).ofMaxLength(200),
                Arbitraries.just("PHYSICIST")
            ).as(UserRegistrationRequest::new)
        );
    }

    @Provide
    Arbitrary<UserRegistrationRequest> requestsWithWhitespaceFields() {
        return Arbitraries.oneOf(
            // Whitespace-only email
            Arbitraries.just(new UserRegistrationRequest("   ", "ValidPass123!", "John", "Doe", "CERN", "PHYSICIST")),
            // Whitespace-only first name
            Arbitraries.just(new UserRegistrationRequest("test@physics.org", "ValidPass123!", "   ", "Doe", "CERN", "PHYSICIST")),
            // Whitespace-only last name
            Arbitraries.just(new UserRegistrationRequest("test@physics.org", "ValidPass123!", "John", "   ", "CERN", "PHYSICIST")),
            // Whitespace-only role
            Arbitraries.just(new UserRegistrationRequest("test@physics.org", "ValidPass123!", "John", "Doe", "CERN", "   "))
        );
    }

    @Provide
    Arbitrary<String> namesWithSpecialChars() {
        return Arbitraries.oneOf(
            Arbitraries.strings().withChars("!@#$%^&*()").ofMinLength(1).ofMaxLength(10),
            Arbitraries.strings().withChars("123456789").ofMinLength(1).ofMaxLength(10),
            Arbitraries.strings().withChars("<>{}[]").ofMinLength(1).ofMaxLength(10),
            Arbitraries.strings().withChars("'\"\\").ofMinLength(1).ofMaxLength(10),
            Arbitraries.just("O'Connor"), // Valid name with apostrophe
            Arbitraries.just("Jean-Luc"), // Valid name with hyphen
            Arbitraries.just("José"), // Valid name with accent
            Arbitraries.just("李明") // Valid name with non-Latin characters
        );
    }

    @Provide
    Arbitrary<String> invalidRoles() {
        return Arbitraries.oneOf(
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20), // Random strings
            Arbitraries.just("INVALID_ROLE"),
            Arbitraries.just("SUPER_ADMIN"),
            Arbitraries.just("ROOT"),
            Arbitraries.just(""),
            Arbitraries.just("   "),
            Arbitraries.just((String) null)
        );
    }
}