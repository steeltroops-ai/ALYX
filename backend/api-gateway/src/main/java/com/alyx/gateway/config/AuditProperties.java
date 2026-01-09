package com.alyx.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Configuration properties for security audit logging.
 * Controls audit log behavior and retention policies.
 */
@Configuration
@ConfigurationProperties(prefix = "security.audit")
@Validated
public class AuditProperties {

    /**
     * Whether audit logging is enabled.
     */
    @NotNull
    private Boolean enabled = true;

    /**
     * Number of days to retain audit logs.
     */
    @Min(value = 1, message = "Retention days must be at least 1")
    @NotNull
    private Integer retentionDays = 90;

    /**
     * Whether to log successful login attempts.
     */
    @NotNull
    private Boolean logSuccessfulLogins = true;

    /**
     * Whether to log failed authentication attempts.
     */
    @NotNull
    private Boolean logFailedAttempts = true;

    /**
     * Whether to log suspicious activity.
     */
    @NotNull
    private Boolean logSuspiciousActivity = true;

    // Getters and setters
    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(Integer retentionDays) {
        this.retentionDays = retentionDays;
    }

    public Boolean getLogSuccessfulLogins() {
        return logSuccessfulLogins;
    }

    public void setLogSuccessfulLogins(Boolean logSuccessfulLogins) {
        this.logSuccessfulLogins = logSuccessfulLogins;
    }

    public Boolean getLogFailedAttempts() {
        return logFailedAttempts;
    }

    public void setLogFailedAttempts(Boolean logFailedAttempts) {
        this.logFailedAttempts = logFailedAttempts;
    }

    public Boolean getLogSuspiciousActivity() {
        return logSuspiciousActivity;
    }

    public void setLogSuspiciousActivity(Boolean logSuspiciousActivity) {
        this.logSuspiciousActivity = logSuspiciousActivity;
    }
}