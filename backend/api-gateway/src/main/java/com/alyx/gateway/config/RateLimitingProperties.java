package com.alyx.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Configuration properties for rate limiting and account lockout.
 * Controls authentication attempt limits and lockout behavior.
 */
@Configuration
@ConfigurationProperties(prefix = "security.rate-limiting")
@Validated
public class RateLimitingProperties {

    /**
     * Maximum number of failed login attempts before account lockout.
     */
    @Min(value = 1, message = "Login attempts must be at least 1")
    @NotNull
    private Integer loginAttempts = 5;

    /**
     * Account lockout duration in seconds.
     */
    @Min(value = 60, message = "Lockout duration must be at least 60 seconds")
    @NotNull
    private Integer lockoutDuration = 900; // 15 minutes

    /**
     * Default requests per minute for standard users.
     */
    @Min(value = 1, message = "Default requests per minute must be at least 1")
    @NotNull
    private Integer defaultRequestsPerMinute = 100;

    /**
     * Requests per minute for premium users.
     */
    @Min(value = 1, message = "Premium requests per minute must be at least 1")
    @NotNull
    private Integer premiumRequestsPerMinute = 500;

    /**
     * Requests per minute for admin users.
     */
    @Min(value = 1, message = "Admin requests per minute must be at least 1")
    @NotNull
    private Integer adminRequestsPerMinute = 1000;

    /**
     * Rate limiting window size in minutes.
     */
    @Min(value = 1, message = "Window size must be at least 1 minute")
    @NotNull
    private Integer windowSizeMinutes = 1;

    // Getters and setters
    public Integer getLoginAttempts() {
        return loginAttempts;
    }

    public void setLoginAttempts(Integer loginAttempts) {
        this.loginAttempts = loginAttempts;
    }

    public Integer getLockoutDuration() {
        return lockoutDuration;
    }

    public void setLockoutDuration(Integer lockoutDuration) {
        this.lockoutDuration = lockoutDuration;
    }

    public Integer getDefaultRequestsPerMinute() {
        return defaultRequestsPerMinute;
    }

    public void setDefaultRequestsPerMinute(Integer defaultRequestsPerMinute) {
        this.defaultRequestsPerMinute = defaultRequestsPerMinute;
    }

    public Integer getPremiumRequestsPerMinute() {
        return premiumRequestsPerMinute;
    }

    public void setPremiumRequestsPerMinute(Integer premiumRequestsPerMinute) {
        this.premiumRequestsPerMinute = premiumRequestsPerMinute;
    }

    public Integer getAdminRequestsPerMinute() {
        return adminRequestsPerMinute;
    }

    public void setAdminRequestsPerMinute(Integer adminRequestsPerMinute) {
        this.adminRequestsPerMinute = adminRequestsPerMinute;
    }

    public Integer getWindowSizeMinutes() {
        return windowSizeMinutes;
    }

    public void setWindowSizeMinutes(Integer windowSizeMinutes) {
        this.windowSizeMinutes = windowSizeMinutes;
    }
}