package com.alyx.gateway.service;

import com.alyx.gateway.config.PasswordSecurityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.regex.Pattern;

/**
 * Password security service for secure password hashing and validation.
 * 
 * Provides bcrypt-based password hashing with configurable salt rounds,
 * password strength validation, and constant-time password verification
 * to prevent timing attacks.
 */
@Service
public class PasswordService {

    private static final Logger logger = LoggerFactory.getLogger(PasswordService.class);
    
    private final BCryptPasswordEncoder passwordEncoder;
    private final PasswordSecurityProperties passwordProperties;
    
    // Pre-compiled regex patterns for password validation
    private static final Pattern UPPERCASE_PATTERN = Pattern.compile(".*[A-Z].*");
    private static final Pattern LOWERCASE_PATTERN = Pattern.compile(".*[a-z].*");
    private static final Pattern DIGIT_PATTERN = Pattern.compile(".*\\d.*");
    private static final Pattern SPECIAL_CHAR_PATTERN = Pattern.compile(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*");

    public PasswordService(PasswordSecurityProperties passwordProperties) {
        this.passwordProperties = passwordProperties;
        // Initialize BCrypt encoder with configured rounds and secure random
        this.passwordEncoder = new BCryptPasswordEncoder(
            passwordProperties.getBcryptRounds(), 
            new SecureRandom()
        );
        
        logger.info("PasswordService initialized with {} bcrypt rounds", 
            passwordProperties.getBcryptRounds());
    }

    /**
     * Hash a plaintext password using bcrypt with configured salt rounds.
     * 
     * @param plainPassword the plaintext password to hash
     * @return the bcrypt hashed password
     * @throws IllegalArgumentException if password is null or empty
     */
    public String hashPassword(String plainPassword) {
        if (plainPassword == null || plainPassword.trim().isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }
        
        logger.debug("Hashing password with {} bcrypt rounds", passwordProperties.getBcryptRounds());
        
        try {
            String hashedPassword = passwordEncoder.encode(plainPassword);
            logger.debug("Password successfully hashed");
            return hashedPassword;
        } catch (Exception e) {
            logger.error("Error hashing password", e);
            throw new RuntimeException("Failed to hash password", e);
        }
    }

    /**
     * Verify a plaintext password against a bcrypt hash using constant-time comparison.
     * This method is designed to prevent timing attacks by always taking the same
     * amount of time regardless of whether the password matches or not.
     * 
     * @param plainPassword the plaintext password to verify
     * @param hashedPassword the bcrypt hash to verify against
     * @return true if the password matches the hash, false otherwise
     */
    public boolean verifyPassword(String plainPassword, String hashedPassword) {
        if (plainPassword == null || hashedPassword == null) {
            // Perform a dummy bcrypt operation to maintain constant time
            passwordEncoder.matches("dummy", "$2a$12$dummy.hash.to.prevent.timing.attacks");
            return false;
        }
        
        try {
            // BCrypt's matches method already implements constant-time comparison
            boolean matches = passwordEncoder.matches(plainPassword, hashedPassword);
            
            if (matches) {
                logger.debug("Password verification successful");
            } else {
                logger.debug("Password verification failed");
            }
            
            return matches;
        } catch (Exception e) {
            logger.error("Error verifying password", e);
            // Perform dummy operation to maintain constant time even on error
            passwordEncoder.matches("dummy", "$2a$12$dummy.hash.to.prevent.timing.attacks");
            return false;
        }
    }

    /**
     * Check if a password meets the configured strength requirements.
     * 
     * @param password the password to validate
     * @return true if the password meets all requirements, false otherwise
     */
    public boolean isPasswordStrong(String password) {
        if (password == null) {
            return false;
        }
        
        try {
            validatePasswordStrength(password);
            return true;
        } catch (IllegalArgumentException e) {
            logger.debug("Password strength validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Validate password strength and throw detailed exception if requirements are not met.
     * 
     * @param password the password to validate
     * @throws IllegalArgumentException with specific validation error message
     */
    public void validatePasswordStrength(String password) {
        if (password == null) {
            throw new IllegalArgumentException("Password cannot be null");
        }
        
        // Check minimum length
        if (password.length() < passwordProperties.getMinLength()) {
            throw new IllegalArgumentException(
                String.format("Password must be at least %d characters long", 
                    passwordProperties.getMinLength())
            );
        }
        
        // Check uppercase requirement
        if (passwordProperties.getRequireUppercase() && !UPPERCASE_PATTERN.matcher(password).matches()) {
            throw new IllegalArgumentException("Password must contain at least one uppercase letter");
        }
        
        // Check lowercase requirement
        if (passwordProperties.getRequireLowercase() && !LOWERCASE_PATTERN.matcher(password).matches()) {
            throw new IllegalArgumentException("Password must contain at least one lowercase letter");
        }
        
        // Check number requirement
        if (passwordProperties.getRequireNumbers() && !DIGIT_PATTERN.matcher(password).matches()) {
            throw new IllegalArgumentException("Password must contain at least one number");
        }
        
        // Check special character requirement
        if (passwordProperties.getRequireSpecialChars() && !SPECIAL_CHAR_PATTERN.matcher(password).matches()) {
            throw new IllegalArgumentException("Password must contain at least one special character");
        }
        
        logger.debug("Password strength validation passed");
    }

    /**
     * Get the current bcrypt rounds configuration.
     * 
     * @return the number of bcrypt rounds being used
     */
    public int getBcryptRounds() {
        return passwordProperties.getBcryptRounds();
    }

    /**
     * Check if the given hash was created with the current bcrypt rounds.
     * This can be used to determine if a password needs to be re-hashed
     * with updated security parameters.
     * 
     * @param hashedPassword the bcrypt hash to check
     * @return true if the hash uses current rounds, false otherwise
     */
    public boolean isHashUpToDate(String hashedPassword) {
        if (hashedPassword == null || !hashedPassword.startsWith("$2")) {
            return false;
        }
        
        try {
            // Extract rounds from bcrypt hash format: $2a$rounds$salt+hash
            String[] parts = hashedPassword.split("\\$");
            if (parts.length >= 3) {
                int hashRounds = Integer.parseInt(parts[2]);
                return hashRounds == passwordProperties.getBcryptRounds();
            }
        } catch (NumberFormatException e) {
            logger.warn("Could not parse bcrypt rounds from hash", e);
        }
        
        return false;
    }

    /**
     * Generate a secure random password that meets all strength requirements.
     * This can be used for temporary passwords or password reset scenarios.
     * 
     * @param length the desired password length (minimum is the configured minimum length)
     * @return a randomly generated password that meets all requirements
     */
    public String generateSecurePassword(int length) {
        int actualLength = Math.max(length, passwordProperties.getMinLength());
        
        String uppercase = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String lowercase = "abcdefghijklmnopqrstuvwxyz";
        String digits = "0123456789";
        String specialChars = "!@#$%^&*()_+-=[]{}|;:,.<>?";
        
        StringBuilder password = new StringBuilder();
        SecureRandom random = new SecureRandom();
        
        // Ensure at least one character from each required category
        if (passwordProperties.getRequireUppercase()) {
            password.append(uppercase.charAt(random.nextInt(uppercase.length())));
        }
        if (passwordProperties.getRequireLowercase()) {
            password.append(lowercase.charAt(random.nextInt(lowercase.length())));
        }
        if (passwordProperties.getRequireNumbers()) {
            password.append(digits.charAt(random.nextInt(digits.length())));
        }
        if (passwordProperties.getRequireSpecialChars()) {
            password.append(specialChars.charAt(random.nextInt(specialChars.length())));
        }
        
        // Fill remaining length with random characters from all categories
        String allChars = uppercase + lowercase + digits + specialChars;
        while (password.length() < actualLength) {
            password.append(allChars.charAt(random.nextInt(allChars.length())));
        }
        
        // Shuffle the password to avoid predictable patterns
        char[] passwordArray = password.toString().toCharArray();
        for (int i = passwordArray.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char temp = passwordArray[i];
            passwordArray[i] = passwordArray[j];
            passwordArray[j] = temp;
        }
        
        return new String(passwordArray);
    }
}