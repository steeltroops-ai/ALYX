package com.alyx.gateway.service;

import com.alyx.gateway.config.PasswordSecurityProperties;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.NumericChars;
import net.jqwik.api.constraints.StringLength;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for password security consistency.
 * 
 * **Feature: neon-auth-system, Property 7: Password Security Consistency**
 * **Validates: Requirements 3.1, 3.4**
 * 
 * Tests universal properties of password hashing and security validation
 * to ensure consistent behavior across all possible inputs.
 */
@Tag("Feature: neon-auth-system, Property 7: Password Security Consistency")
class PasswordSecurityConsistencyPropertyTest {

    private PasswordService passwordService;
    private PasswordSecurityProperties passwordProperties;

    @BeforeEach
    void setUp() {
        // Set up password properties with default secure configuration
        passwordProperties = new PasswordSecurityProperties();
        passwordProperties.setBcryptRounds(12);
        passwordProperties.setMinLength(8);
        passwordProperties.setRequireUppercase(true);
        passwordProperties.setRequireLowercase(true);
        passwordProperties.setRequireNumbers(true);
        passwordProperties.setRequireSpecialChars(true);
        
        passwordService = new PasswordService(passwordProperties);
    }

    /**
     * Property 7a: Password hashing consistency
     * For any valid password, hashing should always produce a different hash
     * but verification should always succeed with the original password.
     */
    @Property(tries = 20)
    void passwordHashingConsistency(@ForAll("validPasswords") String password) {
        // When hashing the same password multiple times
        String hash1 = passwordService.hashPassword(password);
        String hash2 = passwordService.hashPassword(password);
        
        // Then hashes should be different (due to salt)
        assertNotEquals(hash1, hash2, "Hashes should be different due to salt");
        
        // But both should verify successfully with original password
        assertTrue(passwordService.verifyPassword(password, hash1), 
            "First hash should verify with original password");
        assertTrue(passwordService.verifyPassword(password, hash2), 
            "Second hash should verify with original password");
        
        // And both should fail with wrong password
        assertFalse(passwordService.verifyPassword("wrongpassword", hash1), 
            "First hash should not verify with wrong password");
        assertFalse(passwordService.verifyPassword("wrongpassword", hash2), 
            "Second hash should not verify with wrong password");
    }

    /**
     * Property 7b: Password never stored in plaintext
     * For any password, the hash should never equal the original password.
     */
    @Property(tries = 20)
    void passwordNeverStoredInPlaintext(@ForAll("validPasswords") String password) {
        // When hashing a password
        String hash = passwordService.hashPassword(password);
        
        // Then hash should never equal the original password
        assertNotEquals(password, hash, "Hash should never equal original password");
        
        // And hash should start with bcrypt identifier
        assertTrue(hash.startsWith("$2"), "Hash should start with bcrypt identifier");
        
        // And hash should contain the correct number of bcrypt rounds
        String[] parts = hash.split("\\$");
        assertEquals("12", parts[2], "Hash should contain configured bcrypt rounds");
    }

    /**
     * Property 7c: Strong password validation consistency
     * For any password that meets all requirements, isPasswordStrong should return true
     * and validatePasswordStrength should not throw exceptions.
     */
    @Property(tries = 20)
    void strongPasswordValidationConsistency(@ForAll("strongPasswords") String password) {
        // When checking password strength
        boolean isStrong = passwordService.isPasswordStrong(password);
        
        // Then it should be considered strong
        assertTrue(isStrong, "Password meeting all requirements should be considered strong");
        
        // And validation should not throw exception
        assertDoesNotThrow(() -> passwordService.validatePasswordStrength(password),
            "Strong password validation should not throw exception");
    }

    /**
     * Property 7d: Weak password rejection consistency
     * For any password that doesn't meet requirements, isPasswordStrong should return false
     * and validatePasswordStrength should throw IllegalArgumentException.
     */
    @Property(tries = 20)
    void weakPasswordRejectionConsistency(@ForAll("weakPasswords") String password) {
        // When checking weak password strength
        boolean isStrong = passwordService.isPasswordStrong(password);
        
        // Then it should not be considered strong
        assertFalse(isStrong, "Password not meeting requirements should not be considered strong");
        
        // And validation should throw exception
        assertThrows(IllegalArgumentException.class, 
            () -> passwordService.validatePasswordStrength(password),
            "Weak password validation should throw IllegalArgumentException");
    }

    /**
     * Property 7e: Hash format consistency
     * For any valid password, the generated hash should always follow bcrypt format
     * and be compatible with the current bcrypt rounds configuration.
     */
    @Property(tries = 20)
    void hashFormatConsistency(@ForAll("validPasswords") String password) {
        // When hashing a password
        String hash = passwordService.hashPassword(password);
        
        // Then hash should follow bcrypt format: $2a$rounds$salt+hash
        String[] parts = hash.split("\\$");
        assertEquals(4, parts.length, "Hash should have 4 parts separated by $");
        assertEquals("2a", parts[1], "Hash should use bcrypt 2a variant");
        assertEquals("12", parts[2], "Hash should use configured rounds");
        assertEquals(53, parts[3].length(), "Salt+hash part should be 53 characters");
        
        // And hash should be considered up to date
        assertTrue(passwordService.isHashUpToDate(hash), 
            "Newly generated hash should be up to date");
    }

    /**
     * Property 7f: Null and empty input handling
     * For any null or empty input, the service should handle gracefully without crashes.
     */
    @Property(tries = 10)
    void nullAndEmptyInputHandling(@ForAll("nullOrEmptyStrings") String input) {
        // When processing null or empty inputs
        // Then hashing should throw IllegalArgumentException
        assertThrows(IllegalArgumentException.class, 
            () -> passwordService.hashPassword(input),
            "Hashing null/empty password should throw IllegalArgumentException");
        
        // And strength validation should return false or throw exception
        assertFalse(passwordService.isPasswordStrong(input), 
            "Null/empty password should not be considered strong");
        
        assertThrows(IllegalArgumentException.class, 
            () -> passwordService.validatePasswordStrength(input),
            "Validating null/empty password should throw IllegalArgumentException");
    }

    /**
     * Property 7g: Generated password security
     * For any generated secure password, it should meet all strength requirements
     * and be hashable and verifiable.
     */
    @Property(tries = 10)
    void generatedPasswordSecurity(@ForAll @IntRange(min = 8, max = 50) int length) {
        // When generating a secure password
        String generatedPassword = passwordService.generateSecurePassword(length);
        
        // Then it should meet all strength requirements
        assertTrue(passwordService.isPasswordStrong(generatedPassword), 
            "Generated password should be strong");
        
        assertDoesNotThrow(() -> passwordService.validatePasswordStrength(generatedPassword),
            "Generated password should pass validation");
        
        // And it should be hashable and verifiable
        String hash = passwordService.hashPassword(generatedPassword);
        assertTrue(passwordService.verifyPassword(generatedPassword, hash),
            "Generated password should be hashable and verifiable");
        
        // And it should have at least the requested length
        assertTrue(generatedPassword.length() >= Math.max(length, 8),
            "Generated password should have at least requested length");
    }

    // Arbitraries for generating test data

    @Provide
    Arbitrary<String> validPasswords() {
        return Combinators.combine(
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(5), // base
            Arbitraries.strings().withCharRange('A', 'Z').ofMinLength(1).ofMaxLength(3), // uppercase
            Arbitraries.strings().withCharRange('a', 'z').ofMinLength(1).ofMaxLength(3), // lowercase
            Arbitraries.strings().numeric().ofMinLength(1).ofMaxLength(3), // numbers
            Arbitraries.strings().withChars("!@#$%^&*()").ofMinLength(1).ofMaxLength(2) // special
        ).as((base, upper, lower, num, special) -> base + upper + lower + num + special);
    }

    @Provide
    Arbitrary<String> strongPasswords() {
        return Combinators.combine(
            Arbitraries.strings().withCharRange('A', 'Z').ofMinLength(1).ofMaxLength(5), // uppercase
            Arbitraries.strings().withCharRange('a', 'z').ofMinLength(1).ofMaxLength(5), // lowercase
            Arbitraries.strings().numeric().ofMinLength(1).ofMaxLength(3), // numbers
            Arbitraries.strings().withChars("!@#$%^&*()_+-=[]{}|;:,.<>?").ofMinLength(1).ofMaxLength(3), // special
            Arbitraries.strings().alpha().ofMinLength(0).ofMaxLength(10) // additional chars
        ).as((upper, lower, num, special, extra) -> {
            String password = upper + lower + num + special + extra;
            // Ensure minimum length
            while (password.length() < 8) {
                password += "Aa1!";
            }
            return password;
        });
    }

    @Provide
    Arbitrary<String> weakPasswords() {
        return Arbitraries.oneOf(
            // Too short
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(7),
            // No uppercase
            Arbitraries.strings().withCharRange('a', 'z').ofMinLength(8).ofMaxLength(15)
                .map(s -> s + "123!"),
            // No lowercase
            Arbitraries.strings().withCharRange('A', 'Z').ofMinLength(8).ofMaxLength(15)
                .map(s -> s + "123!"),
            // No numbers
            Arbitraries.strings().alpha().ofMinLength(8).ofMaxLength(15)
                .map(s -> s + "!@#"),
            // No special characters
            Arbitraries.strings().alpha().numeric().ofMinLength(8).ofMaxLength(15)
        );
    }

    @Provide
    Arbitrary<String> nullOrEmptyStrings() {
        return Arbitraries.oneOf(
            Arbitraries.just((String) null),
            Arbitraries.just(""),
            Arbitraries.just("   "), // whitespace only
            Arbitraries.just("\t\n") // tabs and newlines
        );
    }
}