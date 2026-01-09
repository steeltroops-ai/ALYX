package com.alyx.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Configuration properties for password security settings.
 * Validates bcrypt rounds and password requirements.
 */
@Configuration
@ConfigurationProperties(prefix = "security.password")
@Validated
public class PasswordSecurityProperties {

    /**
     * Number of bcrypt rounds for password hashing.
     * Minimum 10, maximum 15 for security vs performance balance.
     */
    @Min(value = 10, message = "Bcrypt rounds must be at least 10 for security")
    @Max(value = 15, message = "Bcrypt rounds should not exceed 15 for performance")
    @NotNull
    private Integer bcryptRounds = 12;

    /**
     * Minimum password length requirement.
     */
    @Min(value = 8, message = "Minimum password length must be at least 8")
    @NotNull
    private Integer minLength = 8;

    /**
     * Whether to require uppercase letters in passwords.
     */
    @NotNull
    private Boolean requireUppercase = true;

    /**
     * Whether to require lowercase letters in passwords.
     */
    @NotNull
    private Boolean requireLowercase = true;

    /**
     * Whether to require numbers in passwords.
     */
    @NotNull
    private Boolean requireNumbers = true;

    /**
     * Whether to require special characters in passwords.
     */
    @NotNull
    private Boolean requireSpecialChars = true;

    // Getters and setters
    public Integer getBcryptRounds() {
        return bcryptRounds;
    }

    public void setBcryptRounds(Integer bcryptRounds) {
        this.bcryptRounds = bcryptRounds;
    }

    public Integer getMinLength() {
        return minLength;
    }

    public void setMinLength(Integer minLength) {
        this.minLength = minLength;
    }

    public Boolean getRequireUppercase() {
        return requireUppercase;
    }

    public void setRequireUppercase(Boolean requireUppercase) {
        this.requireUppercase = requireUppercase;
    }

    public Boolean getRequireLowercase() {
        return requireLowercase;
    }

    public void setRequireLowercase(Boolean requireLowercase) {
        this.requireLowercase = requireLowercase;
    }

    public Boolean getRequireNumbers() {
        return requireNumbers;
    }

    public void setRequireNumbers(Boolean requireNumbers) {
        this.requireNumbers = requireNumbers;
    }

    public Boolean getRequireSpecialChars() {
        return requireSpecialChars;
    }

    public void setRequireSpecialChars(Boolean requireSpecialChars) {
        this.requireSpecialChars = requireSpecialChars;
    }
}