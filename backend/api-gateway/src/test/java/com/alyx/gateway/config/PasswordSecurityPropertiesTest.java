package com.alyx.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for password security configuration properties.
 * Verifies proper loading and validation of security settings.
 */
@SpringBootTest
@ActiveProfiles("test")
class PasswordSecurityPropertiesTest {

    @Autowired
    private PasswordSecurityProperties passwordSecurityProperties;

    @Test
    void shouldLoadPasswordSecurityProperties() {
        // Given the configuration properties are loaded
        assertThat(passwordSecurityProperties).isNotNull();
        
        // Then should have expected default values for test profile
        assertThat(passwordSecurityProperties.getBcryptRounds()).isEqualTo(4);
        assertThat(passwordSecurityProperties.getMinLength()).isEqualTo(8);
        assertThat(passwordSecurityProperties.getRequireUppercase()).isTrue();
        assertThat(passwordSecurityProperties.getRequireLowercase()).isTrue();
        assertThat(passwordSecurityProperties.getRequireNumbers()).isTrue();
        assertThat(passwordSecurityProperties.getRequireSpecialChars()).isTrue();
    }

    @Test
    void shouldHaveValidBcryptRounds() {
        // Given the bcrypt rounds configuration
        Integer bcryptRounds = passwordSecurityProperties.getBcryptRounds();
        
        // Then should be within valid range
        assertThat(bcryptRounds).isBetween(4, 15);
    }
}