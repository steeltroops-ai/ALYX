package com.alyx.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Simple test to verify Neon database configuration loads without errors.
 * This test validates that all configuration properties and beans are properly configured.
 */
@SpringBootTest(classes = {
    DatabaseConfig.class,
    PasswordSecurityProperties.class,
    AuditProperties.class,
    RateLimitingProperties.class
})
@ActiveProfiles("test")
class NeonDatabaseConfigurationTest {

    @Test
    void contextLoads() {
        // This test passes if the Spring context loads successfully
        // with all the Neon database configuration
    }
}